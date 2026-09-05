package dev.whysoezzy.meet.di

import com.whysoezzy.auth.domain.models.AuthClearResult
import com.whysoezzy.auth.domain.models.AuthOperationPermit
import com.whysoezzy.auth.domain.models.ClearReservation
import com.whysoezzy.auth.domain.repository.AuthSessionRepository
import com.whysoezzy.auth.domain.usecase.LogoutUseCase
import com.whysoezzy.domain.models.PushInstallationDeleteResult
import com.whysoezzy.domain.usecase.DeleteCurrentUserProfileUseCase
import dev.whysoezzy.meet.push.AccountExitLease
import dev.whysoezzy.meet.push.PushRegistrationCoordinator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedAccountExitCoordinatorTest {
    private val deleteProfile: DeleteCurrentUserProfileUseCase = mockk()
    private val logout: LogoutUseCase = mockk()
    private val auth: AuthSessionRepository = mockk()
    private val push: PushRegistrationCoordinator = mockk()
    private val lease: AccountExitLease = mockk()
    private val permit: AuthOperationPermit = mockk()
    private val clearReservation: ClearReservation = mockk()

    private fun coordinator(): BoundedAccountExitCoordinator {
        every { auth.captureAuthOperationPermit() } returns permit
        every { auth.reserveClear(permit) } returns clearReservation
        coEvery { auth.clearReserved(clearReservation) } returns AuthClearResult.Cleared
        every { push.beginAccountExitLease() } returns lease
        every { push.endAccountExit(lease) } returns Unit
        every { push.unregisterFirebase() } returns CompletableDeferred(Result.success(Unit))
        return BoundedAccountExitCoordinator(
            deleteCurrentUserProfile = deleteProfile,
            logoutUseCase = logout,
            authSessionRepository = auth,
            pushRegistrationCoordinator = push,
        )
    }

    @Test
    fun `explicit logout deletes installation and performs one server-only logout and clear`() = runTest {
        val installationId = "550e8400-e29b-41d4-a716-446655440000"
        coEvery {
            push.clearAccountState(any(), true)
        } returns installationId
        coEvery {
            push.deleteInstallation(installationId)
        } returns Result.success(PushInstallationDeleteResult.Acknowledged)
        coEvery {
            push.recordAccountCleanupOutcome(installationId, any())
        } returns Unit
        coEvery { logout.requestServerLogout() } returns Result.success(Unit)

        coordinator().logout()

        coVerify(exactly = 1) { push.deleteInstallation(installationId) }
        coVerify(exactly = 1) { push.recordAccountCleanupOutcome(installationId, any()) }
        coVerify(exactly = 1) { logout.requestServerLogout() }
        coVerify(exactly = 1) { auth.clearReserved(clearReservation) }
        verify(exactly = 1) { push.endAccountExit(lease) }
        coVerify(exactly = 0) { logout() }
    }

    @Test
    fun `forced logout skips installation deletion and server request`() = runTest {
        coEvery { push.clearAccountState() } returns null

        coordinator().forcedLogout()

        coVerify(exactly = 0) { push.deleteInstallation(any()) }
        coVerify(exactly = 0) { logout.requestServerLogout() }
        coVerify(exactly = 1) { auth.clearReserved(clearReservation) }
        verify(exactly = 1) { push.endAccountExit(lease) }
    }

    @Test
    fun `successful account deletion keeps profile deletion as prerequisite and skips installation delete`() =
        runTest {
            coEvery { deleteProfile() } returns Result.success(Unit)
            coEvery { push.clearAccountState() } returns null
            coEvery { logout.requestServerLogout() } returns Result.success(Unit)

            assertTrue(coordinator().deleteCurrentAccount().isSuccess)

            coVerify(exactly = 1) { deleteProfile() }
            coVerify(exactly = 0) { push.deleteInstallation(any()) }
            coVerify(exactly = 1) { logout.requestServerLogout() }
            coVerify(exactly = 1) { auth.clearReserved(clearReservation) }
        }

    @Test
    fun `noncooperative state cleanup cannot hold the caller past the exit deadline`() = runTest {
        val cleanupGate = CompletableDeferred<Unit>()
        coEvery { push.clearAccountState() } coAnswers {
            withContext(NonCancellable) {
                cleanupGate.await()
            }
            null
        }

        val started = System.nanoTime()
        coordinator().forcedLogout()
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000L

        assertTrue("exit exceeded deadline: ${elapsedMillis}ms", elapsedMillis < 4_750L)
        verify(exactly = 1) { push.endAccountExit(lease) }
        coVerify(exactly = 1) { auth.clearReserved(clearReservation) }
        cleanupGate.complete(Unit)
    }

    @Test
    fun `reservation setup failure still releases the account exit lease`() = runTest {
        coEvery { push.clearAccountState() } returns null
        val exitCoordinator = coordinator()
        every { auth.reserveClear(permit) } throws IllegalStateException("reservation failed")

        exitCoordinator.forcedLogout()

        verify(exactly = 1) { push.endAccountExit(lease) }
        coVerify(exactly = 0) { auth.clearReserved(any()) }
    }
}
