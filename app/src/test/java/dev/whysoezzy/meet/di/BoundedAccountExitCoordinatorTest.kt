package dev.whysoezzy.meet.di

import com.whysoezzy.auth.domain.repository.AuthSessionRepository
import com.whysoezzy.auth.domain.usecase.LogoutUseCase
import com.whysoezzy.domain.usecase.DeleteCurrentUserProfileUseCase
import dev.whysoezzy.meet.push.PushRegistrationCoordinator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedAccountExitCoordinatorTest {
    private val deleteProfile: DeleteCurrentUserProfileUseCase = mockk()
    private val logout: LogoutUseCase = mockk()
    private val auth: AuthSessionRepository = mockk()
    private val push: PushRegistrationCoordinator = mockk()

    private fun coordinator() = BoundedAccountExitCoordinator(
        deleteCurrentUserProfile = deleteProfile,
        logoutUseCase = logout,
        authSessionRepository = auth,
        pushRegistrationCoordinator = push,
    )

    @Test
    fun `successful account deletion never deletes an installation and clears auth`() = runTest {
        coEvery { deleteProfile() } returns Result.success(Unit)
        coEvery {
            push.clearAccountState(any())
        } returns "550e8400-e29b-41d4-a716-446655440000"
        every { push.unregisterFirebase() } returns Unit
        coEvery { logout() } returns Unit
        coEvery { auth.clear() } returns Unit

        assertTrue(coordinator().deleteCurrentAccount().isSuccess)

        coVerify(exactly = 0) {
            push.deleteInstallation(any())
        }
        coVerify { auth.clear() }
    }

    @Test
    fun `logout attempts auth clear when push cleanup throws`() = runTest {
        coEvery {
            push.clearAccountState(any())
        } throws IllegalStateException("store failure")
        coEvery { auth.clear() } returns Unit

        val outcome = runCatching { coordinator().logout() }

        assertTrue(outcome.exceptionOrNull() is IllegalStateException)
        coVerify { auth.clear() }
    }
}
