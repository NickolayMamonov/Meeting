package com.whysoezzy.auth.di

import com.whysoezzy.auth.TokenManager
import com.whysoezzy.auth.domain.models.AuthClearResult
import com.whysoezzy.auth.domain.models.AuthCredentialIdentity
import com.whysoezzy.auth.domain.models.AuthOperationPermit
import com.whysoezzy.auth.domain.models.AuthSession
import com.whysoezzy.auth.domain.models.ClearReservation
import com.whysoezzy.auth.domain.models.CredentialVersion
import com.whysoezzy.auth.domain.models.PersistedTokenPair
import com.whysoezzy.auth.domain.models.RefreshOutcome
import com.whysoezzy.auth.domain.repository.AuthRepository
import com.whysoezzy.common.error.AppException
import com.whysoezzy.network.error.ApiException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthorizedTokenRefresherTest {
    private val authRepository: AuthRepository = mockk()
    private val tokenManager: TokenManager = mockk(relaxed = true)
    private val permit = AuthOperationPermit(
        generation = 4L,
        identity = AuthCredentialIdentity(
            userId = 7L,
            stage = AuthSession.Stage.Ready,
            credentialVersion = CredentialVersion("epoch", 2L),
            refreshToken = "refresh-token",
        ),
    )
    private val clearReservation = ClearReservation(5L, 1L, permit.identity)

    init {
        every { tokenManager.isLoggedInFlow } returns emptyFlow()
        every { tokenManager.captureAuthOperationPermit() } returns permit
        every { tokenManager.reserveClear(permit) } returns clearReservation
        coEvery { tokenManager.clearReserved(clearReservation) } returns AuthClearResult.Cleared
    }

    @Test
    fun `invalid refresh reserves and clears local tokens`() = runTest {
        coEvery { authRepository.refreshToken(permit) } returns
            RefreshOutcome.Unauthorized(permit)

        val tokens = refreshAuthorizedTokens(authRepository, tokenManager)

        assertNull(tokens)
        coVerify(exactly = 1) { tokenManager.reserveClear(permit) }
        coVerify(exactly = 1) { tokenManager.clearReserved(clearReservation) }
    }

    @Test
    fun `network unauthorized refresh reserves and clears local tokens`() = runTest {
        coEvery { authRepository.refreshToken(permit) } returns
            RefreshOutcome.Unauthorized(permit)

        val tokens = refreshAuthorizedTokens(authRepository, tokenManager)

        assertNull(tokens)
        coVerify(exactly = 1) { tokenManager.clearReserved(clearReservation) }
    }

    @Test
    fun `transient refresh failure preserves local tokens`() = runTest {
        coEvery { authRepository.refreshToken(permit) } returns
            RefreshOutcome.TransientFailure(AppException.NetworkError("offline"))

        val tokens = refreshAuthorizedTokens(authRepository, tokenManager)

        assertNull(tokens)
        coVerify(exactly = 0) { tokenManager.reserveClear(any()) }
        coVerify(exactly = 0) { tokenManager.clearReserved(any()) }
    }

    @Test
    fun `missing refresh credential reserves and clears local tokens`() = runTest {
        coEvery { authRepository.refreshToken(permit) } returns
            RefreshOutcome.Missing(permit)

        val tokens = refreshAuthorizedTokens(authRepository, tokenManager)

        assertNull(tokens)
        coVerify(exactly = 1) { tokenManager.clearReserved(clearReservation) }
    }

    @Test
    fun `successful refresh returns the repository persisted token pair without rereading`() = runTest {
        coEvery { authRepository.refreshToken(permit) } returns
            RefreshOutcome.Refreshed(
                PersistedTokenPair("new-access-token", "new-refresh-token"),
            )

        val tokens = refreshAuthorizedTokens(authRepository, tokenManager)

        assertEquals("new-access-token" to "new-refresh-token", tokens)
        coVerify(exactly = 0) { tokenManager.getRefreshToken() }
        coVerify(exactly = 0) { tokenManager.clearReserved(any()) }
    }

    @Test
    fun `stale refresh outcome performs no clear`() = runTest {
        coEvery { authRepository.refreshToken(permit) } returns RefreshOutcome.StaleSkipped

        assertNull(refreshAuthorizedTokens(authRepository, tokenManager))

        coVerify(exactly = 0) { tokenManager.reserveClear(any()) }
        coVerify(exactly = 0) { tokenManager.clearReserved(any()) }
    }

    @Test
    fun `duplicate clear reservation is rejected without a second clear`() = runTest {
        every { tokenManager.reserveClear(permit) } returnsMany
            listOf(clearReservation, null)
        coEvery { authRepository.refreshToken(permit) } returns
            RefreshOutcome.Unauthorized(permit)

        refreshAuthorizedTokens(authRepository, tokenManager)
        refreshAuthorizedTokens(authRepository, tokenManager)

        coVerify(exactly = 1) { tokenManager.clearReserved(clearReservation) }
    }

    @Test
    fun `unexpected unauthorized exception is not converted into a local clear`() = runTest {
        coEvery { authRepository.refreshToken(permit) } throws ApiException.UnauthorizedError()

        assertNull(refreshAuthorizedTokens(authRepository, tokenManager))

        coVerify(exactly = 0) { tokenManager.reserveClear(any()) }
    }
}
