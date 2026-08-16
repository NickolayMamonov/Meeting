package dev.whysoezzy.meet.di

import com.whysoezzy.auth.domain.repository.AuthSessionRepository
import com.whysoezzy.auth.domain.usecase.LogoutUseCase
import com.whysoezzy.domain.usecase.DeleteCurrentUserProfileUseCase
import dev.whysoezzy.meet.push.PushRegistrationCoordinator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
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
        coEvery { push.beginAccountExit() } returns Unit
        coEvery { push.endAccountExit() } returns Unit
        every { push.unregisterFirebase() } returns Unit
        coEvery { logout() } returns Unit
        coEvery { auth.clear() } returns Unit

        assertTrue(coordinator().deleteCurrentAccount().isSuccess)

        coVerify(exactly = 0) {
            push.deleteInstallation(any())
        }
        coVerify { auth.clear() }
        coVerify { push.beginAccountExit() }
        coVerify { push.endAccountExit() }
    }

    @Test
    fun `forced logout never deletes an installation`() = runTest {
        coEvery { push.beginAccountExit() } returns Unit
        coEvery { push.endAccountExit() } returns Unit
        coEvery { push.clearAccountState(any()) } returns
            "550e8400-e29b-41d4-a716-446655440000"
        every { push.unregisterFirebase() } returns Unit
        coEvery { auth.clear() } returns Unit

        coordinator().forcedLogout()

        coVerify(exactly = 0) { push.deleteInstallation(any()) }
        coVerify(exactly = 0) { logout() }
        coVerify { push.beginAccountExit() }
        coVerify { push.endAccountExit() }
    }

    @Test
    fun `account deletion bounds state cleanup to 500 milliseconds`() = runTest {
        coEvery { deleteProfile() } returns Result.success(Unit)
        coEvery { push.beginAccountExit() } returns Unit
        coEvery { push.endAccountExit() } returns Unit
        coEvery { push.clearAccountState(any()) } coAnswers { awaitCancellation() }
        every { push.unregisterFirebase() } returns Unit
        coEvery { logout() } returns Unit
        coEvery { auth.clear() } returns Unit

        assertTrue(coordinator().deleteCurrentAccount().isSuccess)
        coVerify { auth.clear() }
        coVerify { push.unregisterFirebase() }
    }

    @Test
    fun `logout attempts auth clear when push cleanup throws`() = runTest {
        coEvery { push.beginAccountExit() } returns Unit
        coEvery { push.endAccountExit() } returns Unit
        coEvery {
            push.clearAccountState(any(), true)
        } throws IllegalStateException("store failure")
        coEvery { auth.clear() } returns Unit

        val outcome = runCatching { coordinator().logout() }

        assertTrue(outcome.exceptionOrNull() is IllegalStateException)
        coVerify { auth.clear() }
    }

    @Test
    fun `explicit logout persists installation delete transport outcome`() = runTest {
        val installationId = "550e8400-e29b-41d4-a716-446655440000"
        coEvery { push.beginAccountExit() } returns Unit
        coEvery { push.endAccountExit() } returns Unit
        coEvery {
            push.clearAccountState(any(), true)
        } returns installationId
        every { push.unregisterFirebase() } returns Unit
        coEvery {
            push.deleteInstallation(installationId)
        } returns Result.failure(IllegalStateException("offline"))
        coEvery {
            push.recordAccountCleanupOutcome(
                installationId,
                any(),
            )
        } returns Unit
        coEvery { logout() } returns Unit
        coEvery { auth.clear() } returns Unit

        coordinator().logout()

        coVerify(exactly = 1) {
            push.recordAccountCleanupOutcome(installationId, any())
        }
    }

    @Test
    fun `auth is cleared when exit fence acquisition fails`() = runTest {
        coEvery { push.beginAccountExit() } throws IllegalStateException("fence failure")
        coEvery { auth.clear() } returns Unit

        val outcome = runCatching { coordinator().forcedLogout() }

        assertTrue(outcome.exceptionOrNull() is IllegalStateException)
        coVerify { auth.clear() }
        coVerify(exactly = 0) { push.endAccountExit() }
    }
}
