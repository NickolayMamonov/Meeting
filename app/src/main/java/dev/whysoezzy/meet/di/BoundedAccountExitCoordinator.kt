package dev.whysoezzy.meet.di

import com.whysoezzy.auth.domain.repository.AuthSessionRepository
import com.whysoezzy.auth.domain.usecase.LogoutUseCase
import com.whysoezzy.domain.usecase.AccountExitCoordinator
import com.whysoezzy.domain.usecase.DeleteCurrentUserProfileUseCase
import dev.whysoezzy.meet.push.PushRegistrationCoordinator
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal class BoundedAccountExitCoordinator(
    private val deleteCurrentUserProfile: DeleteCurrentUserProfileUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val authSessionRepository: AuthSessionRepository,
    private val pushRegistrationCoordinator: PushRegistrationCoordinator,
) : AccountExitCoordinator {
    override suspend fun logout() {
        withContext(NonCancellable) {
            pushRegistrationCoordinator.beginAccountExit()
            try {
                withTimeoutOrNull(4_000L) {
                    val installationId = withTimeoutOrNull(500L) {
                        pushRegistrationCoordinator.clearAccountState()
                    }
                    pushRegistrationCoordinator.unregisterFirebase()
                    if (installationId != null) {
                        withTimeoutOrNull(1_250L) {
                            pushRegistrationCoordinator.deleteInstallation(installationId)
                        }
                    }
                    withTimeoutOrNull(1_250L) { logoutUseCase() }
                }
            } finally {
                runCatching {
                    withTimeoutOrNull(750L) { authSessionRepository.clear() }
                }
                pushRegistrationCoordinator.endAccountExit()
            }
        }
    }

    override suspend fun forcedLogout() {
        withContext(NonCancellable) {
            pushRegistrationCoordinator.beginAccountExit()
            try {
                withTimeoutOrNull(4_000L) {
                    withTimeoutOrNull(500L) {
                        pushRegistrationCoordinator.clearAccountState()
                    }
                    pushRegistrationCoordinator.unregisterFirebase()
                    withTimeoutOrNull(1_250L) { logoutUseCase() }
                }
            } finally {
                runCatching {
                    withTimeoutOrNull(750L) { authSessionRepository.clear() }
                }
                pushRegistrationCoordinator.endAccountExit()
            }
        }
    }

    override suspend fun deleteCurrentAccount(): Result<Unit> {
        val deletion = deleteCurrentUserProfile()
        if (deletion.isFailure) return deletion
        withContext(NonCancellable) {
            pushRegistrationCoordinator.beginAccountExit()
            try {
                withTimeoutOrNull(4_000L) {
                    withTimeoutOrNull(500L) {
                        pushRegistrationCoordinator.clearAccountState()
                    }
                    pushRegistrationCoordinator.unregisterFirebase()
                    withTimeoutOrNull(1_250L) { logoutUseCase() }
                }
            } finally {
                runCatching {
                    withTimeoutOrNull(750L) { authSessionRepository.clear() }
                }
                pushRegistrationCoordinator.endAccountExit()
            }
        }
        return Result.success(Unit)
    }
}
