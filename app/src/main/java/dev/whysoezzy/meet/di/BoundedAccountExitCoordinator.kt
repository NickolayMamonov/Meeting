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
            try {
                pushRegistrationCoordinator.beginAccountExit()
                try {
                    withTimeoutOrNull(4_000L) {
                        val installationId = withTimeoutOrNull(500L) {
                            pushRegistrationCoordinator.clearAccountState(
                                retainInstallationCleanup = true,
                            )
                        }
                        if (installationId != null) {
                            val deleteResult = withTimeoutOrNull(1_250L) {
                                pushRegistrationCoordinator.deleteInstallation(installationId)
                            }
                            pushRegistrationCoordinator.recordAccountCleanupOutcome(
                                installationId,
                                deleteResult ?: Result.failure(
                                    IllegalStateException(
                                        "Timed out deleting account installation",
                                    ),
                                ),
                            )
                        }
                        pushRegistrationCoordinator.unregisterFirebase()
                        withTimeoutOrNull(1_250L) { logoutUseCase() }
                    }
                } finally {
                    pushRegistrationCoordinator.endAccountExit()
                }
            } finally {
                runCatching {
                    withTimeoutOrNull(750L) { authSessionRepository.clear() }
                }
            }
        }
    }

    override suspend fun forcedLogout() {
        withContext(NonCancellable) {
            try {
                pushRegistrationCoordinator.beginAccountExit()
                try {
                    withTimeoutOrNull(4_000L) {
                        withTimeoutOrNull(500L) {
                            pushRegistrationCoordinator.clearAccountState()
                        }
                        pushRegistrationCoordinator.unregisterFirebase()
                    }
                } finally {
                    pushRegistrationCoordinator.endAccountExit()
                }
            } finally {
                runCatching {
                    withTimeoutOrNull(750L) { authSessionRepository.clear() }
                }
            }
        }
    }

    override suspend fun deleteCurrentAccount(): Result<Unit> {
        val deletion = deleteCurrentUserProfile()
        if (deletion.isFailure) return deletion
        withContext(NonCancellable) {
            try {
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
                    pushRegistrationCoordinator.endAccountExit()
                }
            } finally {
                runCatching {
                    withTimeoutOrNull(750L) { authSessionRepository.clear() }
                }
            }
        }
        return Result.success(Unit)
    }
}
