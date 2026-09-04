package dev.whysoezzy.meet.di

import com.whysoezzy.auth.domain.models.AuthClearResult
import com.whysoezzy.auth.domain.models.AuthOperationPermit
import com.whysoezzy.auth.domain.models.ClearReservation
import com.whysoezzy.auth.domain.repository.AuthSessionRepository
import com.whysoezzy.auth.domain.usecase.LogoutUseCase
import com.whysoezzy.domain.models.PushInstallationDeleteResult
import com.whysoezzy.domain.usecase.AccountExitCoordinator
import com.whysoezzy.domain.usecase.DeleteCurrentUserProfileUseCase
import dev.whysoezzy.meet.push.AccountExitLease
import dev.whysoezzy.meet.push.PushRegistrationCoordinator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * Owns the short user-facing account-exit window while all actual writes/effects
 * continue in the auth and push completion scopes after a waiter times out.
 */
internal class BoundedAccountExitCoordinator(
    private val deleteCurrentUserProfile: DeleteCurrentUserProfileUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val authSessionRepository: AuthSessionRepository,
    private val pushRegistrationCoordinator: PushRegistrationCoordinator,
) : AccountExitCoordinator {
    private val completionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun logout() {
        withContext(NonCancellable) {
            runExit(
                deleteInstallation = true,
                serverLogout = true,
            )
        }
    }

    override suspend fun forcedLogout() {
        withContext(NonCancellable) {
            runExit(
                deleteInstallation = false,
                serverLogout = false,
            )
        }
    }

    override suspend fun deleteCurrentAccount(): Result<Unit> {
        val deletion = deleteCurrentUserProfile()
        if (deletion.isFailure) return deletion
        withContext(NonCancellable) {
            runExit(
                deleteInstallation = false,
                serverLogout = true,
            )
        }
        return Result.success(Unit)
    }

    private suspend fun runExit(
        deleteInstallation: Boolean,
        serverLogout: Boolean,
    ) {
        val startedAt = System.nanoTime()
        val permit = authSessionRepository.captureAuthOperationPermit()
        val lease = pushRegistrationCoordinator.beginAccountExitLease()
        try {
            val installationId = awaitWithin(
                submit { pushRegistrationCoordinator.clearAccountState(
                    retainInstallationCleanup = deleteInstallation,
                ) },
                timeoutMillis = remainingMillis(startedAt, STATE_CLEAR_BUDGET_MILLIS),
            ).getOrNull()

            if (deleteInstallation && installationId != null) {
                val deleteResult = awaitWithin(
                    submit { pushRegistrationCoordinator.deleteInstallation(installationId) },
                    timeoutMillis = remainingMillis(startedAt, INSTALLATION_DELETE_DEADLINE_MILLIS),
                ).getOrElse {
                    Result.failure(it)
                }
                awaitWithin(
                    submit {
                        pushRegistrationCoordinator.recordAccountCleanupOutcome(
                            installationId,
                            deleteResult,
                        )
                    },
                    timeoutMillis = remainingMillis(
                        startedAt,
                        CLEANUP_OUTCOME_DEADLINE_MILLIS,
                    ),
                )
            }

            awaitWithin(
                pushRegistrationCoordinator.unregisterFirebase(),
                timeoutMillis = remainingMillis(
                    startedAt,
                    UNREGISTER_DEADLINE_MILLIS,
                ),
            )

            if (serverLogout) {
                awaitWithin(
                    submit { logoutUseCase.requestServerLogout() },
                    timeoutMillis = remainingMillis(
                        startedAt,
                        SERVER_LOGOUT_DEADLINE_MILLIS,
                    ),
                )
            }
        } finally {
            /*
             * Reservation is deliberately made once, after the bounded cleanup
             * phase. Awaiting its independent writer is read-only: timeout or
             * caller cancellation cannot cancel the conditional DataStore write.
             */
            val reservation = authSessionRepository.reserveClear(permit)
            if (reservation != null) {
                val clear = submit {
                    authSessionRepository.clearReserved(reservation)
                }
                awaitWithin(
                    clear,
                    timeoutMillis = remainingMillis(startedAt, EXIT_DEADLINE_MILLIS),
                )
            }
            pushRegistrationCoordinator.endAccountExit(lease)
        }
    }

    private fun <T> submit(block: suspend () -> T): Deferred<Result<T>> =
        completionScope.async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { block() }
        }

    private suspend fun <T> awaitWithin(
        deferred: Deferred<Result<T>>,
        timeoutMillis: Long,
    ): Result<T> =
        if (timeoutMillis <= 0L) {
            Result.failure(ExitTimeoutException)
        } else {
            withTimeoutOrNull(timeoutMillis) { deferred.await() }
                ?: Result.failure(ExitTimeoutException)
        }

    private fun remainingMillis(startedAt: Long, deadlineMillis: Long): Long =
        (deadlineMillis - (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND)
            .coerceAtLeast(0L)

    private companion object {
        const val EXIT_DEADLINE_MILLIS = 4_750L
        const val STATE_CLEAR_BUDGET_MILLIS = 500L
        const val INSTALLATION_DELETE_DEADLINE_MILLIS = 1_250L
        const val CLEANUP_OUTCOME_DEADLINE_MILLIS = 4_000L
        const val UNREGISTER_DEADLINE_MILLIS = 4_000L
        const val SERVER_LOGOUT_DEADLINE_MILLIS = 4_000L
        const val NANOS_PER_MILLISECOND = 1_000_000L
        val ExitTimeoutException = IllegalStateException("Account exit deadline elapsed")
    }
}
