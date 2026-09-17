package com.whysoezzy.auth.data.repository

import com.whysoezzy.auth.TokenManager
import com.whysoezzy.auth.data.api.AuthApi
import com.whysoezzy.auth.data.dto.AuthResponse
import com.whysoezzy.auth.data.dto.AuthUserDto
import com.whysoezzy.auth.data.dto.RefreshTokenResponse
import com.whysoezzy.auth.data.dto.SendOtpResponse
import com.whysoezzy.auth.domain.models.AuthClearResult
import com.whysoezzy.auth.domain.models.AuthCredentialIdentity
import com.whysoezzy.auth.domain.models.AuthCredentialRead
import com.whysoezzy.auth.domain.models.AuthCredentialSnapshot
import com.whysoezzy.auth.domain.models.AuthFailure
import com.whysoezzy.auth.domain.models.AuthOperationPermit
import com.whysoezzy.auth.domain.models.AuthOutcome
import com.whysoezzy.auth.domain.models.AuthRefreshSaveResult
import com.whysoezzy.auth.domain.models.AuthResult
import com.whysoezzy.auth.domain.models.AuthSaveResult
import com.whysoezzy.auth.domain.models.AuthSession
import com.whysoezzy.auth.domain.models.ClearReservation
import com.whysoezzy.auth.domain.models.CredentialVersion
import com.whysoezzy.auth.domain.models.OwnerSaveReservation
import com.whysoezzy.auth.domain.models.PersistedTokenPair
import com.whysoezzy.auth.domain.models.RefreshOutcome
import com.whysoezzy.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
    private val refreshPermit = AuthOperationPermit(
        generation = 2L,
        identity = AuthCredentialIdentity(
            userId = 1L,
            stage = AuthSession.Stage.Ready,
            credentialVersion = CredentialVersion("epoch", 0L),
            refreshToken = "oldRefresh",
        ),
    )
    private val refreshSnapshot = AuthCredentialSnapshot(
        accessToken = "oldAccess",
        refreshToken = "oldRefresh",
        userId = 1L,
        stage = AuthSession.Stage.Ready,
        credentialVersion = CredentialVersion("epoch", 0L),
    )
    private val ownerPermit = AuthOperationPermit(1L, null)
    private val ownerReservation = OwnerSaveReservation(1L, 1L, ownerPermit)
    private val clearReservation = ClearReservation(3L, 3L, refreshPermit.identity)

    private fun repository(): AuthRepositoryImpl {
        // isLoggedInFlow нужен для делегирования в isLoggedInFlow property
        every { tokenManager.isLoggedInFlow } returns MutableStateFlow(false)
        every { tokenManager.captureAuthOperationPermit() } returns refreshPermit
        coEvery { tokenManager.readCredentialSnapshot(refreshPermit) } returns
            AuthCredentialRead.Present(refreshSnapshot, refreshPermit)
        every { tokenManager.reserveOwnerSave() } returns ownerReservation
        coEvery {
            tokenManager.saveAuthenticated(ownerReservation, any(), any(), any(), any())
        } returns AuthSaveResult.Persisted
        every { tokenManager.reserveClear(refreshPermit) } returns clearReservation
        coEvery { tokenManager.clearReserved(clearReservation) } returns AuthClearResult.Cleared
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
        coEvery {
            tokenManager.saveAuthenticated(ownerReservation, any(), any(), any(), any())
        } returns AuthSaveResult.Persisted

        val result = repository().verifyEmailOtp("person@example.com", "123456")

        assertTrue(result is AuthOutcome.Success)
        val authResult: AuthResult = (result as AuthOutcome.Success).value
        assertEquals("access123", authResult.accessToken)
        assertEquals(false, authResult.isNewUser)
        assertEquals(1L, authResult.userId)

        // Проверяем что saveTokens вызван с правильными токенами
        coVerify {
            tokenManager.saveAuthenticated(
                ownerReservation,
                "access123",
                "refresh456",
                1L,
                AuthSession.Stage.Ready,
            )
        }
    }

    @Test
    fun `token persistence cancellation is propagated`() = runTest {
        coEvery { authApi.verifyEmailOtp(any(), any(), any(), any()) } returns successAuthResponse()
        val repository = repository()
        coEvery {
            tokenManager.saveAuthenticated(ownerReservation, any(), any(), any(), any())
        } throws CancellationException("cancelled")

        try {
            repository.verifyEmailOtp("person@example.com", "123456")
            org.junit.Assert.fail("CancellationException must propagate")
        } catch (_: CancellationException) {
            // expected
        }
    }

    @Test
    fun `email verification isNewUser=true propagates to AuthResult`() = runTest {
        coEvery { authApi.verifyEmailOtp(any(), any(), any(), any()) } returns
            successAuthResponse(isNewUser = true)
        val repository = repository()
        coEvery {
            tokenManager.saveAuthenticated(ownerReservation, any(), any(), any(), any())
        } returns AuthSaveResult.Persisted

        val result = repository.verifyEmailOtp("person@example.com", "123456")

        assertTrue((result as AuthOutcome.Success).value.isNewUser)
    }

    @Test
    fun `email verification failure returns typed failure without saving tokens`() = runTest {
        coEvery { authApi.verifyEmailOtp(any(), any(), any(), any()) } throws
            RuntimeException("Invalid code")

        val result = repository().verifyEmailOtp("person@example.com", "000000")

        assertTrue(result is AuthOutcome.Failure)
        coVerify(exactly = 0) {
            tokenManager.saveAuthenticated(ownerReservation, any(), any(), any(), any())
        }
    }

    // ==================== refreshToken ====================

    @Test
    fun `refreshToken success saves new tokens and returns access token`() = runTest {
        coEvery { authApi.refreshToken("oldRefresh") } returns
            RefreshTokenResponse(accessToken = "newAccess", refreshToken = "newRefresh")
        coEvery { tokenManager.saveRefreshedTokens(refreshPermit, any(), any()) } returns
            AuthRefreshSaveResult.Persisted(PersistedTokenPair("newAccess", "newRefresh"))

        val result = repository().refreshToken(refreshPermit)

        assertTrue(result is RefreshOutcome.Refreshed)
        assertEquals(
            PersistedTokenPair("newAccess", "newRefresh"),
            (result as RefreshOutcome.Refreshed).pair,
        )
        coVerify {
            tokenManager.saveRefreshedTokens(refreshPermit, "newAccess", "newRefresh")
        }
    }

    @Test
    fun `refreshToken with null stored token returns failure`() = runTest {
        val repository = repository()
        coEvery { tokenManager.readCredentialSnapshot(refreshPermit) } returns
            AuthCredentialRead.Missing(refreshPermit)
        val result = repository.refreshToken(refreshPermit)
        assertTrue(result is RefreshOutcome.Missing)
        coVerify(exactly = 0) { authApi.refreshToken(any()) }
    }

    @Test
    fun `refreshToken API failure returns Result failure`() = runTest {
        coEvery { authApi.refreshToken(any()) } throws RuntimeException("Server error")

        val result = repository().refreshToken(refreshPermit)

        assertTrue(result is RefreshOutcome.TransientFailure)
    }

    // ==================== logout ====================

    @Test
    fun `logout always clears tokens even if API call fails`() = runTest {
        // Сервер вернул ошибку — токены всё равно должны быть удалены
        coEvery { authApi.logout() } throws RuntimeException("Server error")
        repository().logout()

        coVerify(exactly = 1) { tokenManager.clearReserved(clearReservation) }
    }

    @Test
    fun `logout on success also clears tokens`() = runTest {
        coEvery { authApi.logout() } returns mapOf("message" to "ok")
        repository().logout()

        coVerify(exactly = 1) { tokenManager.clearReserved(clearReservation) }
    }

    @Test
    fun `logout still attempts one fenced clear when credential read fails`() = runTest {
        coEvery { tokenManager.readCredentialSnapshot(refreshPermit) } throws
            IllegalStateException("credential store unavailable")
        coEvery { authApi.logout() } returns mapOf("message" to "ok")

        repository().logout()

        coVerify(exactly = 1) { authApi.logout() }
        coVerify(exactly = 1) { tokenManager.reserveClear(refreshPermit) }
        coVerify(exactly = 1) { tokenManager.clearReserved(clearReservation) }
    }

    @Test
    fun `stale credential read retains the original fence instead of clearing a newer owner`() =
        runTest {
            coEvery { tokenManager.readCredentialSnapshot(refreshPermit) } returns
                AuthCredentialRead.Stale
            coEvery { authApi.logout() } returns mapOf("message" to "ok")

            repository().logout()

            coVerify(exactly = 1) { tokenManager.reserveClear(refreshPermit) }
            coVerify(exactly = 1) { tokenManager.clearReserved(clearReservation) }
        }

    @Test
    fun `verification persistence failure makes one generation-safe clear reservation`() = runTest {
        val repository = repository()
        val failedClearReservation = ClearReservation(2L, 2L, ownerPermit.identity)
        every { tokenManager.reserveClear(ownerPermit) } returns failedClearReservation
        coEvery { tokenManager.clearReserved(failedClearReservation) } returns AuthClearResult.Cleared
        coEvery {
            tokenManager.saveAuthenticated(ownerReservation, any(), any(), any(), any())
        } throws IllegalStateException("persistence failed")
        coEvery { authApi.verifyEmailOtp(any(), any(), any(), any()) } returns successAuthResponse()

        val result = repository.verifyEmailOtp("person@example.com", "123456")

        assertEquals(
            AuthFailure.SessionPersistenceFailure,
            (result as AuthOutcome.Failure).reason,
        )
        coVerify(exactly = 1) { tokenManager.reserveClear(ownerPermit) }
        coVerify(exactly = 1) { tokenManager.clearReserved(failedClearReservation) }
    }

    @Test
    fun `server-only logout never clears local credentials`() = runTest {
        coEvery { authApi.logout() } returns mapOf("message" to "ok")

        val result = repository().requestServerLogout()

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { tokenManager.reserveClear(any()) }
        coVerify(exactly = 0) { tokenManager.clearReserved(any()) }
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
        val job = launch { repository().logout() }
        advanceTimeBy(100)
        job.cancelAndJoin()

        // clearTokens должен выполниться несмотря на отмену (NonCancellable в finally)
        coVerify(exactly = 1) { tokenManager.clearReserved(clearReservation) }
    }

    // ==================== Fixtures ====================

    private fun successAuthResponse(isNewUser: Boolean = false) = AuthResponse(
        accessToken = "access123",
        refreshToken = "refresh456",
        isNewUser = isNewUser,
        user = AuthUserDto(id = 1L, name = "Иван", surname = "Иванов"),
    )
}
