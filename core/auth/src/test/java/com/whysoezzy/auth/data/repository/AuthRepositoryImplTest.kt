package com.whysoezzy.auth.data.repository

import com.whysoezzy.auth.TokenManager
import com.whysoezzy.auth.data.api.AuthApi
import com.whysoezzy.auth.data.dto.AuthResponse
import com.whysoezzy.auth.data.dto.AuthUserDto
import com.whysoezzy.auth.data.dto.RefreshTokenResponse
import com.whysoezzy.auth.data.dto.SendOtpResponse
import com.whysoezzy.auth.domain.models.AuthOutcome
import com.whysoezzy.auth.domain.models.AuthResult
import com.whysoezzy.network.TokenSnapshot
import com.whysoezzy.network.error.ApiException
import com.whysoezzy.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryImplTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authApi: AuthApi = mockk()
    private val tokenManager: TokenManager = mockk(relaxed = true)

    private fun repository(): AuthRepositoryImpl {
        // isLoggedInFlow нужен для делегирования в isLoggedInFlow property
        every { tokenManager.isLoggedInFlow } returns MutableStateFlow(false)
        return AuthRepositoryImpl(authApi, tokenManager)
    }

    // ==================== email OTP request ====================

    @Test
    fun `email request success returns typed success`() = runTest {
        coEvery { authApi.requestEmailOtp(any()) } returns SendOtpResponse("ok")

        val result = repository().requestEmailOtp("person@example.com")

        assertTrue(result is AuthOutcome.Success)
    }

    @Test
    fun `email request failure returns typed failure`() = runTest {
        coEvery { authApi.requestEmailOtp(any()) } throws RuntimeException("Network error")

        val result = repository().requestEmailOtp("person@example.com")

        assertTrue(result is AuthOutcome.Failure)
    }

    // ==================== email OTP verification ====================

    @Test
    fun `email verification success saves tokens and returns AuthResult`() = runTest {
        coEvery { authApi.verifyEmailOtp(any(), any(), any(), any()) } returns successAuthResponse()
        coEvery { tokenManager.saveTokens(any(), any(), any()) } just runs

        val result = repository().verifyEmailOtp("person@example.com", "123456")

        assertTrue(result is AuthOutcome.Success)
        val authResult: AuthResult = (result as AuthOutcome.Success).value
        assertEquals("access123", authResult.accessToken)
        assertEquals(false, authResult.isNewUser)
        assertEquals(1L, authResult.userId)

        // Проверяем что saveTokens вызван с правильными токенами
        coVerify { tokenManager.saveTokens("access123", "refresh456", 1L) }
    }

    @Test
    fun `token persistence cancellation is propagated`() = runTest {
        coEvery { authApi.verifyEmailOtp(any(), any(), any(), any()) } returns successAuthResponse()
        coEvery { tokenManager.saveTokens(any(), any(), any()) } throws CancellationException("cancelled")

        try {
            repository().verifyEmailOtp("person@example.com", "123456")
            org.junit.Assert.fail("CancellationException must propagate")
        } catch (_: CancellationException) {
            // expected
        }
    }

    @Test
    fun `email verification isNewUser=true propagates to AuthResult`() = runTest {
        coEvery { authApi.verifyEmailOtp(any(), any(), any(), any()) } returns
            successAuthResponse(isNewUser = true)
        coEvery { tokenManager.saveTokens(any(), any(), any()) } just runs

        val result = repository().verifyEmailOtp("person@example.com", "123456")

        assertTrue((result as AuthOutcome.Success).value.isNewUser)
    }

    @Test
    fun `email verification failure returns typed failure without saving tokens`() = runTest {
        coEvery { authApi.verifyEmailOtp(any(), any(), any(), any()) } throws
            RuntimeException("Invalid code")

        val result = repository().verifyEmailOtp("person@example.com", "000000")

        assertTrue(result is AuthOutcome.Failure)
        coVerify(exactly = 0) { tokenManager.saveTokens(any(), any(), any()) }
    }

    // ==================== refreshToken ====================

    @Test
    fun `refreshToken success saves new tokens and returns access token`() = runTest {
        coEvery { tokenManager.loadTokens() } returns TokenSnapshot("oldAccess", "oldRefresh")
        coEvery { tokenManager.getUserId() } returns 1L
        coEvery { authApi.refreshToken("oldRefresh") } returns
            RefreshTokenResponse(accessToken = "newAccess", refreshToken = "newRefresh")
        coEvery { tokenManager.saveTokens(any(), any(), any()) } just runs

        val result = repository().refreshToken()

        assertTrue(result.isSuccess)
        assertEquals("newAccess", result.getOrThrow())
        coVerify { tokenManager.saveTokens("newAccess", "newRefresh", 1L) }
    }

    @Test
    fun `refreshToken with null stored token returns failure`() = runTest {
        coEvery { tokenManager.loadTokens() } returns null
        val result = repository().refreshToken()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ApiException.UnauthorizedError)
        coVerify(exactly = 0) { authApi.refreshToken(any()) }
    }

    @Test
    fun `refreshToken API failure returns Result failure`() = runTest {
        coEvery { tokenManager.loadTokens() } returns TokenSnapshot("oldAccess", "oldRefresh")
        coEvery { authApi.refreshToken(any()) } throws RuntimeException("Server error")

        val result = repository().refreshToken()

        assertTrue(result.isFailure)
    }

    // ==================== logout ====================

    @Test
    fun `logout always clears tokens even if API call fails`() = runTest {
        // Сервер вернул ошибку — токены всё равно должны быть удалены
        coEvery { authApi.logout() } throws RuntimeException("Server error")
        coEvery { tokenManager.clearTokens() } just runs

        repository().logout()

        coVerify(exactly = 1) { tokenManager.clearTokens() }
    }

    @Test
    fun `logout on success also clears tokens`() = runTest {
        coEvery { authApi.logout() } returns mapOf("message" to "ok")
        coEvery { tokenManager.clearTokens() } just runs

        repository().logout()

        coVerify(exactly = 1) { tokenManager.clearTokens() }
    }

    // ==================== isLoggedInFlow ====================

    @Test
    fun `isLoggedInFlow delegates to TokenManager`() = runTest {
        val flow = MutableStateFlow(true)
        every { tokenManager.isLoggedInFlow } returns flow

        val repo = AuthRepositoryImpl(authApi, tokenManager)

        assertEquals(flow, repo.isLoggedInFlow)
    }

    @Test
    fun `logout clears tokens even when scope is cancelled`() = runTest {
        coEvery { authApi.logout() } coAnswers {
            // эмулируем долгий серверный logout, во время которого корутину отменят
            kotlinx.coroutines.delay(10_000)
            mapOf("message" to "ok")
        }
        coEvery { tokenManager.clearTokens() } just runs

        val job = launch { repository().logout() }
        advanceTimeBy(100)
        job.cancelAndJoin()

        // clearTokens должен выполниться несмотря на отмену (NonCancellable в finally)
        coVerify(exactly = 1) { tokenManager.clearTokens() }
    }

    // ==================== Fixtures ====================

    private fun successAuthResponse(isNewUser: Boolean = false) = AuthResponse(
        accessToken = "access123",
        refreshToken = "refresh456",
        isNewUser = isNewUser,
        user = AuthUserDto(id = 1L, name = "Иван", surname = "Иванов"),
    )
}
