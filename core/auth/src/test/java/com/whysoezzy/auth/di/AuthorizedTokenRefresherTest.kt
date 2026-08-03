package com.whysoezzy.auth.di

import com.whysoezzy.auth.TokenManager
import com.whysoezzy.auth.domain.repository.AuthRepository
import com.whysoezzy.common.error.AppException
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

    init {
        every { tokenManager.isLoggedInFlow } returns emptyFlow()
    }

    @Test
    fun `invalid refresh clears local tokens`() = runTest {
        coEvery { tokenManager.getRefreshToken() } returns "refresh-token"
        coEvery { authRepository.refreshToken() } returns
            Result.failure(AppException.UnauthorizedError())

        val tokens = refreshAuthorizedTokens(authRepository, tokenManager)

        assertNull(tokens)
        coVerify(exactly = 1) { tokenManager.clearTokens() }
    }

    @Test
    fun `transient refresh failure preserves local tokens`() = runTest {
        coEvery { tokenManager.getRefreshToken() } returns "refresh-token"
        coEvery { authRepository.refreshToken() } returns
            Result.failure(AppException.NetworkError("offline"))

        val tokens = refreshAuthorizedTokens(authRepository, tokenManager)

        assertNull(tokens)
        coVerify(exactly = 0) { tokenManager.clearTokens() }
    }

    @Test
    fun `missing refresh credential clears local tokens`() = runTest {
        coEvery { tokenManager.getRefreshToken() } returns null

        val tokens = refreshAuthorizedTokens(authRepository, tokenManager)

        assertNull(tokens)
        coVerify(exactly = 1) { tokenManager.clearTokens() }
        coVerify(exactly = 0) { authRepository.refreshToken() }
    }

    @Test
    fun `successful refresh returns persisted token pair`() = runTest {
        coEvery { tokenManager.getRefreshToken() } returnsMany
            listOf("old-refresh-token", "new-refresh-token")
        coEvery { authRepository.refreshToken() } returns Result.success("new-access-token")

        val tokens = refreshAuthorizedTokens(authRepository, tokenManager)

        assertEquals("new-access-token" to "new-refresh-token", tokens)
        coVerify(exactly = 0) { tokenManager.clearTokens() }
    }
}
