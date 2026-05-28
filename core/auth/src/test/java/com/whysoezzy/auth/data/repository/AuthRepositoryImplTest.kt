package com.whysoezzy.auth.data.repository

import com.whysoezzy.auth.TokenManager
import com.whysoezzy.auth.data.api.AuthApi
import com.whysoezzy.auth.data.dto.AuthResponse
import com.whysoezzy.auth.data.dto.AuthUserDto
import com.whysoezzy.auth.data.dto.RefreshTokenResponse
import com.whysoezzy.auth.domain.models.AuthResult
import com.whysoezzy.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authApi: AuthApi = mockk()
    // TokenManager — internal class, мокаем через mockkClass
    private val tokenManager: TokenManager = mockk(relaxed = true)

    private fun repository(): AuthRepositoryImpl {
        // isLoggedInFlow нужен для делегирования в isLoggedInFlow property
        every { tokenManager.isLoggedInFlow } returns MutableStateFlow(false)
        return AuthRepositoryImpl(authApi, tokenManager)
    }

    // ==================== sendOtp ====================

    @Test
    fun `sendOtp success returns Result success`() = runTest {
        coEvery { authApi.sendOtp(any()) } returns mapOf("message" to "ok")

        val result = repository().sendOtp("+79991234567")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `sendOtp failure returns Result failure`() = runTest {
        coEvery { authApi.sendOtp(any()) } throws RuntimeException("Network error")

        val result = repository().sendOtp("+79991234567")

        assertTrue(result.isFailure)
    }

    // ==================== verifyOtp ====================

    @Test
    fun `verifyOtp success saves tokens and returns AuthResult`() = runTest {
        coEvery { authApi.verifyOtp(any(), any(), any(), any()) } returns successAuthResponse()
        every { tokenManager.saveTokens(any(), any(), any()) } just runs

        val result = repository().verifyOtp("+79991234567", "1234")

        assertTrue(result.isSuccess)
        val authResult: AuthResult = result.getOrThrow()
        assertEquals("access123", authResult.accessToken)
        assertEquals(false, authResult.isNewUser)
        assertEquals(1L, authResult.userId)

        // Проверяем что saveTokens вызван с правильными токенами
        verify { tokenManager.saveTokens("access123", "refresh456", 1L) }
    }

    @Test
    fun `verifyOtp isNewUser=true propagates to AuthResult`() = runTest {
        coEvery { authApi.verifyOtp(any(), any(), any(), any()) } returns
                successAuthResponse(isNewUser = true)
        every { tokenManager.saveTokens(any(), any(), any()) } just runs

        val result = repository().verifyOtp("+79991234567", "1234")

        assertTrue(result.getOrThrow().isNewUser)
    }

    @Test
    fun `verifyOtp failure returns Result failure without saving tokens`() = runTest {
        coEvery { authApi.verifyOtp(any(), any(), any(), any()) } throws
                RuntimeException("Invalid code")

        val result = repository().verifyOtp("+79991234567", "0000")

        assertTrue(result.isFailure)
        verify(exactly = 0) { tokenManager.saveTokens(any(), any(), any()) }
    }

    // ==================== refreshToken ====================

    @Test
    fun `refreshToken success saves new tokens and returns access token`() = runTest {
        every { tokenManager.getRefreshToken() } returns "oldRefresh"
        every { tokenManager.getUserId() } returns 1L
        coEvery { authApi.refreshToken("oldRefresh") } returns
                RefreshTokenResponse(accessToken = "newAccess", refreshToken = "newRefresh")
        every { tokenManager.saveTokens(any(), any(), any()) } just runs

        val result = repository().refreshToken()

        assertTrue(result.isSuccess)
        assertEquals("newAccess", result.getOrThrow())
        verify { tokenManager.saveTokens("newAccess", "newRefresh", 1L) }
    }

    @Test
    fun `refreshToken with null stored token returns failure`() = runTest {
        every { tokenManager.getRefreshToken() } returns null

        val result = repository().refreshToken()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { authApi.refreshToken(any()) }
    }

    @Test
    fun `refreshToken API failure returns Result failure`() = runTest {
        every { tokenManager.getRefreshToken() } returns "oldRefresh"
        coEvery { authApi.refreshToken(any()) } throws RuntimeException("Server error")

        val result = repository().refreshToken()

        assertTrue(result.isFailure)
    }

    // ==================== logout ====================

    @Test
    fun `logout always clears tokens even if API call fails`() = runTest {
        // Сервер вернул ошибку — токены всё равно должны быть удалены
        coEvery { authApi.logout() } throws RuntimeException("Server error")
        every { tokenManager.clearTokens() } just runs

        repository().logout()

        verify(exactly = 1) { tokenManager.clearTokens() }
    }

    @Test
    fun `logout on success also clears tokens`() = runTest {
        coEvery { authApi.logout() } returns mapOf("message" to "ok")
        every { tokenManager.clearTokens() } just runs

        repository().logout()

        verify(exactly = 1) { tokenManager.clearTokens() }
    }

    // ==================== isLoggedInFlow ====================

    @Test
    fun `isLoggedInFlow delegates to TokenManager`() = runTest {
        val flow = MutableStateFlow(true)
        every { tokenManager.isLoggedInFlow } returns flow

        val repo = AuthRepositoryImpl(authApi, tokenManager)

        assertEquals(flow, repo.isLoggedInFlow)
    }

    // ==================== Fixtures ====================

    private fun successAuthResponse(isNewUser: Boolean = false) = AuthResponse(
        accessToken = "access123",
        refreshToken = "refresh456",
        isNewUser = isNewUser,
        user = AuthUserDto(id = 1L, name = "Иван", surname = "Иванов"),
    )
}