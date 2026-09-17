package dev.whysoezzy.meet.push

import com.whysoezzy.auth.domain.models.AuthSession
import com.whysoezzy.auth.domain.models.CredentialVersion
import com.whysoezzy.auth.domain.repository.AuthSessionRepository
import com.whysoezzy.domain.models.PushInstallationDeleteResult
import com.whysoezzy.domain.models.PushInstallationFid
import com.whysoezzy.domain.models.PushInstallationId
import com.whysoezzy.domain.models.PushInstallationUpsertResult
import com.whysoezzy.domain.repository.PushInstallationRepository
import com.whysoezzy.network.error.ApiException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

internal enum class PushLifecyclePhase {
    OPEN,
    EXITING,
    DRAINING,
    DRAIN_BLOCKED,
    ACTIVATING_CURRENT,
}

internal enum class DrainBlockReason {
    UNREGISTER_TASK,
    UNREGISTER_CALLBACK,
    FINAL_SCRUB,
    SCHEDULER_CANCEL,
    NOTIFICATION_CANCEL,
}

internal enum class LifecycleEffectKind {
    SCHEDULER_ENQUEUE,
    SCHEDULER_CANCEL,
    PRESENT,
    NOTIFICATION_CANCEL,
    NAVIGATION,
}

internal class AccountExitLease internal constructor(
    val id: Long,
    val epoch: Long,
) {
    private val released = AtomicBoolean(false)

    internal fun releaseOnce(): Boolean = released.compareAndSet(false, true)
}

internal data class IngressPermit(
    val id: Long,
    val epoch: Long,
)

internal data class LocalWriteHandle(
    val id: Long,
    val epoch: Long,
    val completion: Deferred<Result<PushStateV1>>,
)

internal enum class RemoteHandleState {
    PENDING,
    STARTED,
    SUPPRESSED,
    COMPLETED,
}

internal data class RemoteGeneration(
    val owner: OwnerSnapshot?,
    val epoch: Long,
    val operation: RegistrationOperation,
    val fid: String?,
    val installationId: String?,
    val credentialVersion: CredentialVersion? = null,
)

internal data class DrainTicket(
    val drainId: Long,
    val attempt: Int,
    val step: DrainBlockReason,
    val subject: String? = null,
)

internal data class EffectTicket(
    val id: Long,
    val epoch: Long?,
    val kind: LifecycleEffectKind,
    val completion: Deferred<Result<Unit>>,
)

internal class PushRegistrationCoordinator(
    private val authSessionRepository: AuthSessionRepository,
    private val installationRepository: PushInstallationRepository,
    private val fcm: FcmRegistrationClient,
    private val stateStore: PushStateStore,
    private val presentation: ReminderPresentationGateway = NoOpReminderPresentationGateway,
    private val workScheduler: PushWorkScheduler = NoOpPushWorkScheduler,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = defaultMainDispatcher(),
    private val beforeEffectInvocation: (LifecycleEffectKind) -> Unit = {},
) : PushMessageHandoff {
    private val monitor = Any()
    private val storeMutex = Mutex()
    private val observationMutex = Mutex()
    private val reconciliationMutex = Mutex()
    private val completionScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val observationScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val ids = AtomicLong()
    private val effects = ArrayDeque<EffectRecord>()
    private val activeEffects = HashMap<String, EffectRecord>()
    private val children = LinkedHashMap<Long, ChildRecord>()
    private val legacyLeases = ArrayDeque<AccountExitLease>()
    private val localWrites = LinkedHashMap<Long, LocalRecord>()
    private val remoteHandles = LinkedHashMap<Long, RemoteRecord>()
    private var effectDraining = false
    private var observationJob: Job? = null
    private var phase = PushLifecyclePhase.OPEN
    private var epoch = 0L
    private var activeLeases = 0
    private var nextLease: AccountExitLease? = null
    private var drainId = 0L
    private var drainStarted = false
    private var drainFailure: DrainTicket? = null
    private var currentActivationUser: Long? = null
    private var firebaseCommand: FirebaseCommand? = null
    private var callbackDebt: CompletableDeferred<Unit>? = null
    private var callbackDebtSatisfied = false
    private var registerCallbackDebt = 0
    private var unregisterTaskSucceeded = false
    private var unregisterInvocations = 0
    private var lastUnregisterCompletion: Deferred<Result<Unit>>? = null
    private var drainBlockedCredentialVersion: CredentialVersion? = null
    private var lastObservedCredentialVersion: CredentialVersion? = null
    private var lastDrainRearmVersion: CredentialVersion? = null
    private val pendingNotificationCancellationIds = LinkedHashSet<String>()

    internal val lifecyclePhase: PushLifecyclePhase
        get() = synchronized(monitor) { phase }

    internal val activeExitLeases: Int
        get() = synchronized(monitor) { activeLeases }

    fun start() {
        synchronized(monitor) {
            if (observationJob?.isActive == true) return
            observationJob = observationScope.launch {
                authSessionRepository.credentialState
                    .distinctUntilChanged()
                    .collect { credentialState ->
                        observeCredentialState(credentialState)
                    }
            }
        }
    }

    fun close() {
        observationScope.coroutineContext[Job]?.cancel()
        completionScope.coroutineContext[Job]?.cancel()
        synchronized(monitor) {
            observationJob = null
            effects.clear()
            activeEffects.clear()
            children.clear()
            localWrites.clear()
            remoteHandles.clear()
            pendingNotificationCancellationIds.clear()
            phase = PushLifecyclePhase.DRAIN_BLOCKED
        }
    }

    /**
     * This is intentionally non-suspending. It only changes ownership under the monitor
     * and submits cancellation work to the independent completion scope.
     */
    internal fun beginAccountExit() {
        val lease = beginAccountExitLease()
        synchronized(monitor) { legacyLeases.addLast(lease) }
    }

    internal fun beginAccountExitLease(): AccountExitLease {
        val toCancel: List<Job>
        val lease: AccountExitLease
        synchronized(monitor) {
            check(activeLeases < Int.MAX_VALUE) { "Account-exit lease count overflow" }
            val startsExitCycle = activeLeases == 0
            epoch++
            phase = PushLifecyclePhase.EXITING
            activeLeases++
            lease = AccountExitLease(ids.incrementAndGet(), epoch)
            nextLease = lease
            children.values.forEach { it.stale = true }
            localWrites.values.forEach { it.stale = true }
            firebaseCommand?.let { if (it.epoch < epoch) it.stale = true }
            if (startsExitCycle) unregisterInvocations = 0
            remoteHandles.values
                .filter { it.state == RemoteHandleState.PENDING }
                .forEach { it.state = RemoteHandleState.SUPPRESSED }
            effects.forEach { if (it.epoch != null && it.epoch < epoch) it.suppressed = true }
            activeEffects.values
                .filter { it.state == RemoteHandleState.PENDING }
                .forEach { it.suppressed = true }
            toCancel = children.values.mapNotNull { it.job }
        }
        toCancel.forEach { it.cancel() }
        return lease
    }

    /**
     * Legacy no-argument release is retained for the existing account-exit adapter. New
     * callers should retain and release the returned lease explicitly.
     */
    internal fun endAccountExit() {
        val lease = synchronized(monitor) {
            if (legacyLeases.isEmpty()) null else legacyLeases.removeLast()
        }
        if (lease != null) endAccountExit(lease)
    }

    internal fun endAccountExit(lease: AccountExitLease) {
        synchronized(monitor) {
            legacyLeases.remove(lease)
        }
        if (!lease.releaseOnce()) return
        val shouldDrain = synchronized(monitor) {
            activeLeases--
            check(activeLeases >= 0) { "Account-exit lease underflow" }
            if (activeLeases == 0 && phase == PushLifecyclePhase.EXITING) {
                phase = PushLifecyclePhase.DRAINING
                drainId++
                drainStarted = false
                true
            } else {
                false
            }
        }
        if (shouldDrain) launchDrain()
    }

    override fun captureExitEpoch(): Long = synchronized(monitor) { epoch }

    fun onRegistered(fid: String) {
        val validFid = runCatching { PushInstallationFid(fid).value }.getOrNull() ?: return
        val command = synchronized(monitor) {
            if (registerCallbackDebt > 0) {
                registerCallbackDebt--
                return@synchronized null
            }
            val command = firebaseCommand
            if (command != null &&
                command.kind == FirebaseKind.REGISTER &&
                command.epoch == epoch &&
                !command.stale &&
                command.state != RemoteHandleState.COMPLETED &&
                command.state != RemoteHandleState.SUPPRESSED &&
                !command.callback.isCompleted
            ) {
                command.fid = validFid
                command.callback.complete(Unit)
                command
            } else {
                null
            }
        }
        if (command == null) return
    }

    fun onUnregistered() {
        val signal = synchronized(monitor) {
            val debt = callbackDebt
            if (debt != null && unregisterTaskSucceeded && !callbackDebtSatisfied) {
                callbackDebtSatisfied = true
                debt
            } else {
                null
            }
        }
        signal?.complete(Unit)
        if (signal != null) {
            synchronized(monitor) {
                if (phase == PushLifecyclePhase.DRAIN_BLOCKED &&
                    drainFailure?.step == DrainBlockReason.UNREGISTER_CALLBACK
                ) {
                    phase = PushLifecyclePhase.DRAINING
                    drainStarted = false
                }
            }
            launchDrainIfNeeded()
        }
    }

    fun unregisterFirebase(): Deferred<Result<Unit>> =
        launchFirebase(FirebaseKind.UNREGISTER, null).completion

    fun onDataMessage(
        data: Map<String, String>,
        hasNotificationBlock: Boolean,
        onComplete: () -> Unit = {},
    ) {
        val completionSignaled = AtomicBoolean(false)
        val admitted = synchronized(monitor) {
            val ingressEpoch = epoch
            if (!isEpochAuthorizedLocked(ingressEpoch, allowActivation = false)) {
                null
            } else {
                val permit = IngressPermit(ids.incrementAndGet(), ingressEpoch)
                val child = completionScope.launch(start = CoroutineStart.LAZY) {
                    try {
                        processDataMessage(data, hasNotificationBlock, permit.epoch)
                    } finally {
                        finishChild(permit)
                        if (completionSignaled.compareAndSet(false, true)) onComplete()
                    }
                }
                children[permit.id] = ChildRecord(permit.id, permit.epoch, job = child)
                child.invokeOnCompletion {
                    finishChild(permit)
                    if (completionSignaled.compareAndSet(false, true)) onComplete()
                }
                child.start()
                permit
            }
        }
        if (admitted == null) {
            onComplete()
            return
        }
    }

    override suspend fun handleDataMessage(
        data: Map<String, String>,
        hasNotificationBlock: Boolean,
        ingressExitEpoch: Long,
    ) {
        val permit = admitIngress(ingressExitEpoch, job = coroutineContext[Job]) ?: return
        try {
            processDataMessage(data, hasNotificationBlock, permit.epoch)
        } finally {
            finishChild(permit)
        }
    }

    internal suspend fun reconcileCurrent(): Boolean {
        val permit = admitIngress(
            captureExitEpoch(),
            allowActivation = true,
            job = coroutineContext[Job],
        ) ?: return true
        return try {
            reconciliationMutex.withLock {
                val credentialState = authSessionRepository.credentialState.first()
                val userId = credentialState.session.userId
                    ?.takeIf { credentialState.session.stage != AuthSession.Stage.LoggedOut }
                    ?: return@withLock true
                reconcile(userId, credentialState.credentialVersion, permit.epoch)
            }
        } finally {
            finishChild(permit)
        }
    }

    internal suspend fun claimTap(command: PushTapCommand): Boolean {
        val permit = admitIngress(captureExitEpoch(), job = coroutineContext[Job]) ?: return false
        return try {
            val session = authSessionRepository.read()
            val state = readState()
            val owner = state.registration.owner
                ?.takeIf { session.userId != null && it.userId == session.userId }
                ?: return false
            val event = state.ledger
                .filterIsInstance<LedgerRecord.OwnedReminderEvent>()
                .firstOrNull {
                    it.eventId == command.eventId &&
                        it.meetingId == command.meetingId &&
                        it.owner == owner &&
                        it.status == OwnedEventStatus.DISPLAYED
                } ?: return false
            val next = writeState(permit.epoch, false) {
                PushStateReducer.claimNavigation(it, event.eventId, owner)
            }.await().getOrNull() ?: return false
            next.ledger.any {
                it is LedgerRecord.OwnedReminderEvent &&
                    it.eventId == event.eventId &&
                    it.status == OwnedEventStatus.NAVIGATION_CLAIMED &&
                    it.owner == owner
            }
        } finally {
            finishChild(permit)
        }
    }

    internal suspend fun consumeTap(
        command: PushTapCommand,
        isAlreadyAtDestination: () -> Boolean,
        navigate: () -> Unit,
    ): Boolean {
        val permit = admitIngress(captureExitEpoch(), job = coroutineContext[Job]) ?: return false
        try {
            val session = authSessionRepository.read()
            val state = readState()
            val owner = state.registration.owner
                ?.takeIf { session.userId != null && it.userId == session.userId }
                ?: return false
            val event = state.ledger
                .filterIsInstance<LedgerRecord.OwnedReminderEvent>()
                .firstOrNull {
                    it.eventId == command.eventId &&
                        it.meetingId == command.meetingId &&
                        it.owner == owner &&
                        it.status == OwnedEventStatus.DISPLAYED
                } ?: return false
            val claimed = writeState(permit.epoch, false) {
                PushStateReducer.claimNavigation(it, event.eventId, owner)
            }.await().getOrNull() ?: return false
            val succeeded = claimed.ledger.any {
                it is LedgerRecord.OwnedReminderEvent &&
                    it.eventId == event.eventId &&
                    it.status == OwnedEventStatus.NAVIGATION_CLAIMED &&
                    it.owner == owner
            }
            if (!succeeded) return false
            val invoked = AtomicBoolean(false)
            val effect = submitEffect(
                epoch = permit.epoch,
                kind = LifecycleEffectKind.NAVIGATION,
                main = true,
                action = {
                    if (!isAlreadyAtDestination()) {
                        navigate()
                    }
                    invoked.set(true)
                },
                dedupeKey = "navigation:${command.eventId}",
            ) ?: return false
            val result = effect.completion.await()
            if (result.isFailure || !invoked.get()) return false
            writeState(permit.epoch, false) {
                PushStateReducer.markNavigated(it, command.eventId, owner)
            }.await()
            return true
        } finally {
            finishChild(permit)
        }
    }

    internal suspend fun completeTap(command: PushTapCommand) {
        val permit = admitIngress(captureExitEpoch(), job = coroutineContext[Job]) ?: return
        try {
            val session = authSessionRepository.read()
            val state = readState()
            val owner = state.registration.owner
                ?.takeIf { session.userId != null && it.userId == session.userId }
                ?: return
            writeState(permit.epoch, false) {
                PushStateReducer.markNavigated(it, command.eventId, owner)
            }.await()
        } finally {
            finishChild(permit)
        }
    }

    internal suspend fun drainPendingDisplays() {
        val permit = admitIngress(
            captureExitEpoch(),
            allowActivation = true,
            job = coroutineContext[Job],
        ) ?: return
        try {
            val session = authSessionRepository.read()
            val state = readState()
            val owner = state.registration.owner
                ?.takeIf { session.stage != AuthSession.Stage.LoggedOut && it.userId == session.userId }
                ?: return
            state.ledger
                .filterIsInstance<LedgerRecord.OwnedReminderEvent>()
                .filter { it.owner == owner && it.status == OwnedEventStatus.PENDING_DISPLAY }
                .forEach { event ->
                    val displayed = AtomicBoolean(false)
                    val ticket = submitEffect(
                        permit.epoch,
                        LifecycleEffectKind.PRESENT,
                        action = {
                            displayed.set(presentation.present(event))
                        },
                        dedupeKey = "present:${event.eventId}",
                    ) ?: return@forEach
                    if (ticket.completion.await().isSuccess && displayed.get()) {
                        writeState(permit.epoch, false) {
                            PushStateReducer.markDisplayed(it, event.eventId, owner)
                        }.await()
                    }
                }
        } finally {
            finishChild(permit)
        }
    }

    suspend fun clearAccountState(
        now: Long = System.currentTimeMillis(),
        retainInstallationCleanup: Boolean = false,
    ): String? {
        val result = finalScrub(now, retainInstallationCleanup)
        return result.installationId
    }

    suspend fun deleteInstallation(installationId: String): Result<PushInstallationDeleteResult> {
        val requestedEpoch = captureExitEpoch()
        val exitAuthorized = synchronized(monitor) {
            activeLeases > 0 &&
                phase == PushLifecyclePhase.EXITING &&
                epoch == requestedEpoch
        }
        if (!exitAuthorized &&
            !isEpochAuthorized(requestedEpoch, allowActivation = true)
        ) {
            return Result.failure(IllegalStateException("Push lifecycle is closed"))
        }
        return remote(
            generation = RemoteGeneration(
                owner = null,
                epoch = requestedEpoch,
                operation = RegistrationOperation.DELETE,
                fid = null,
                installationId = installationId,
            ),
            allowDuringExit = exitAuthorized,
        ) {
            installationRepository.delete(PushInstallationId(installationId))
        }.await()
    }

    internal suspend fun recordAccountCleanupOutcome(
        installationId: String,
        result: Result<PushInstallationDeleteResult>,
    ) {
        val state = readState()
        val pending = state.accountCleanup?.takeIf { it.installationId == installationId } ?: return
        if (result.isAcknowledged()) {
            val authorityEpoch = captureExitEpoch()
            writeState(authorityEpoch, allowDuringExit = true, authorityEpoch = authorityEpoch) {
                PushStateReducer.acknowledgeAccountCleanup(it, pending.owner, installationId)
            }.await()
        } else {
            recordAccountCleanupFailure(pending.owner, installationId, result)
        }
    }

    private suspend fun observeCredentialState(
        credentialState: com.whysoezzy.auth.domain.models.AuthCredentialState,
    ) {
        val shouldRearmDrain = synchronized(monitor) {
            val blockedVersion = drainBlockedCredentialVersion
            val rearm = phase == PushLifecyclePhase.DRAIN_BLOCKED &&
                blockedVersion != null &&
                credentialState.credentialVersion.isAdvancedFrom(blockedVersion) &&
                credentialState.credentialVersion != lastDrainRearmVersion
            if (rearm) {
                lastDrainRearmVersion = credentialState.credentialVersion
                drainFailure = null
                drainStarted = false
                drainId++
                phase = PushLifecyclePhase.DRAINING
            }
            lastObservedCredentialVersion = credentialState.credentialVersion
            rearm
        }
        if (shouldRearmDrain) launchDrain()

        observationMutex.withLock {
            val userId = credentialState.session.userId
                ?.takeIf { credentialState.session.stage != AuthSession.Stage.LoggedOut }
            val state = readState()
            val oldOwner = state.registration.owner
            val pendingCleanup = state.accountCleanup
                ?.takeIf { it.terminal == RegistrationTerminal.NONE }
            val replacement = userId == null ||
                (oldOwner != null && oldOwner.userId != userId) ||
                pendingCleanup != null
            if (replacement) {
                val lease = beginAccountExitLease()
                try {
                    if (pendingCleanup != null && userId != null) {
                        val result = remote(
                            RemoteGeneration(
                                pendingCleanup.owner,
                                lease.epoch,
                                RegistrationOperation.DELETE,
                                null,
                                pendingCleanup.installationId,
                            ),
                            allowDuringExit = true,
                        ) {
                            installationRepository.delete(
                                PushInstallationId(pendingCleanup.installationId),
                            )
                        }.await()
                        if (result.isAcknowledged()) {
                            writeState(lease.epoch, true, authorityEpoch = lease.epoch) {
                                PushStateReducer.acknowledgeAccountCleanup(
                                    it,
                                    pendingCleanup.owner,
                                    pendingCleanup.installationId,
                                )
                            }.await()
                        } else {
                            recordAccountCleanupFailure(
                                pendingCleanup.owner,
                                pendingCleanup.installationId,
                                result,
                            )
                        }
                    }
                    val oldInstallation = oldOwner
                        ?.takeIf { userId != null && it.userId != userId }
                        ?.let { state.registration.installationId }
                    if (oldInstallation != null) {
                        val result = remote(
                            RemoteGeneration(
                                requireNotNull(oldOwner),
                                lease.epoch,
                                RegistrationOperation.DELETE,
                                null,
                                oldInstallation,
                            ),
                            allowDuringExit = true,
                        ) {
                            installationRepository.delete(PushInstallationId(oldInstallation))
                        }.await()
                        if (!result.isAcknowledged()) {
                            recordAccountCleanupFailure(
                                requireNotNull(oldOwner),
                                oldInstallation,
                                result,
                            )
                        }
                    }
                } finally {
                    endAccountExit(lease)
                }
            } else {
                val currentUserId = requireNotNull(userId)
                val activating = state.registration.terminal == RegistrationTerminal.NONE &&
                    (
                        oldOwner == null ||
                            state.registration.pendingFid == null ||
                            state.registration.installationId == null
                    )
                val credentialRearm = oldOwner?.userId == currentUserId &&
                    state.registration.terminal == RegistrationTerminal.BLOCKED_AUTH &&
                    credentialState.credentialVersion.isAdvancedFrom(state.registration)
                val shouldEnqueue = synchronized(monitor) {
                    if (activeLeases != 0) {
                        false
                    } else if (credentialRearm || activating) {
                        currentActivationUser = currentUserId
                        if (phase == PushLifecyclePhase.OPEN ||
                            phase == PushLifecyclePhase.DRAIN_BLOCKED
                        ) {
                            phase = PushLifecyclePhase.ACTIVATING_CURRENT
                        }
                        phase == PushLifecyclePhase.ACTIVATING_CURRENT
                    } else {
                        if (state.registration.terminal != RegistrationTerminal.NONE) {
                            if (phase == PushLifecyclePhase.OPEN) {
                                currentActivationUser = currentUserId
                                phase = PushLifecyclePhase.ACTIVATING_CURRENT
                            }
                            false
                        } else {
                            phase == PushLifecyclePhase.OPEN
                        }
                    }
                }
                if (shouldEnqueue) {
                    enqueueScheduler(
                        captureExitEpoch(),
                        allowActivation = activating || credentialRearm,
                    )
                }
            }
        }
    }

    private suspend fun reconcile(
        userId: Long,
        credentialVersion: CredentialVersion,
        ingressEpoch: Long,
    ): Boolean {
        val state = readState()
        val pendingCleanup = state.accountCleanup
            ?.takeIf { it.terminal == RegistrationTerminal.NONE }
        if (pendingCleanup != null) {
            val result = remote(
                RemoteGeneration(
                    pendingCleanup.owner,
                    ingressEpoch,
                    RegistrationOperation.DELETE,
                    null,
                    pendingCleanup.installationId,
                ),
            ) {
                installationRepository.delete(PushInstallationId(pendingCleanup.installationId))
            }.await()
            if (!result.isAcknowledged()) {
                recordAccountCleanupFailure(
                    pendingCleanup.owner,
                    pendingCleanup.installationId,
                    result,
                )
                return !isTransient(result.exceptionOrNull())
            }
            writeState(ingressEpoch, false) {
                PushStateReducer.acknowledgeAccountCleanup(
                    it,
                    pendingCleanup.owner,
                    pendingCleanup.installationId,
                )
            }.await()
            return reconcile(userId, credentialVersion, ingressEpoch)
        }
        val snapshot = synchronized(monitor) {
            if (!isEpochAuthorizedLocked(ingressEpoch, allowActivation = true)) null else true
        } ?: return true
        if (!snapshot) return true
        val current = readState()
        val session = authSessionRepository.read()
        if (session.stage == AuthSession.Stage.LoggedOut || session.userId != userId) return true
        var registration = current.registration
        val credentialRearm = registration.terminal == RegistrationTerminal.BLOCKED_AUTH &&
            credentialVersion.isAdvancedFrom(registration)
        if (registration.terminal != RegistrationTerminal.NONE && !credentialRearm) return true
        val owner = registration.owner?.takeIf { it.userId == userId }
            ?: OwnerSnapshot(userId, registration.accountGeneration + 1L)
        if (credentialRearm) {
            registration = writeState(ingressEpoch, false) {
                it.copy(
                    registration = it.registration.copy(
                        terminal = RegistrationTerminal.NONE,
                        terminalNonce = 0L,
                        retryAttempt = 0,
                        firebaseRetryAttempt = 0,
                        blockedCredentialEpoch = null,
                        blockedCredentialRevision = null,
                        nonce = it.registration.nonce + 1L,
                    ),
                )
            }.await().getOrThrow().registration
        }
        if (registration.owner != owner || registration.accountGeneration != owner.generation) {
            registration = writeState(ingressEpoch, false) {
                it.copy(
                    registration = it.registration.copy(
                        owner = owner,
                        accountGeneration = owner.generation,
                        nonce = it.registration.nonce + 1L,
                        terminalNonce = 0L,
                    ),
                )
            }.await().getOrThrow().registration
        }
        val fid = registration.pendingFid
        return if (fid == null) {
            ensureFirebaseRegistered(owner, credentialVersion, ingressEpoch)
        } else {
            reconcileWithFid(fid, owner, credentialVersion, ingressEpoch)
        }
    }

    private suspend fun ensureFirebaseRegistered(
        owner: OwnerSnapshot,
        credentialVersion: CredentialVersion,
        ingressEpoch: Long,
    ): Boolean {
        val request = readState().registration
        if (!isEpochAuthorized(ingressEpoch, allowActivation = true)) return true
        if (request.owner != owner) return true
        val command = launchFirebase(FirebaseKind.REGISTER, ingressEpoch, credentialVersion)
        val result = command.completion.await()
        if (result.isFailure) {
            val current = readState()
            if (current.registration.owner != owner) return true
            val attempt = current.registration.firebaseRetryAttempt + 1
            writeState(ingressEpoch, false) {
                if (attempt >= MAX_ATTEMPTS) {
                    PushStateReducer.recordTerminal(
                        it,
                        owner,
                        it.registration.nonce,
                        RegistrationTerminal.SUSPENDED_RETRY_EXHAUSTED,
                    )
                } else {
                    it.copy(
                        registration = it.registration.copy(
                            firebaseRetryAttempt = attempt,
                        ),
                    )
                }
            }.await()
            return attempt >= MAX_ATTEMPTS
        }
        if (!isEpochAuthorized(ingressEpoch, allowActivation = true)) return true
        val callbackFid = command.command.fid ?: return true
        if (authSessionRepository.credentialState.first().credentialVersion != credentialVersion) {
            return true
        }
        val staged = writeState(ingressEpoch, false) {
            PushStateReducer.stageFid(it, callbackFid)
        }.await()
        if (staged.isFailure) return true
        writeState(ingressEpoch, false) {
            PushStateReducer.resetFirebaseRetry(it)
        }.await()
        enqueueScheduler(ingressEpoch, allowActivation = true)
        return true
    }

    private suspend fun reconcileWithFid(
        fid: String,
        owner: OwnerSnapshot,
        credentialVersion: CredentialVersion,
        ingressEpoch: Long,
    ): Boolean {
        if (!isEpochAuthorized(ingressEpoch, allowActivation = true)) return true
        val state = readState()
        val session = authSessionRepository.read()
        if (session.stage == AuthSession.Stage.LoggedOut || session.userId != owner.userId) return true
        val registration = state.registration
        if (registration.pendingFid != fid || registration.owner != owner) return true
        val operation = if (registration.installationId == null) {
            RegistrationOperation.CREATE
        } else {
            RegistrationOperation.ROTATE
        }
        val nonce = registration.nonce + 1L
        val started = writeState(ingressEpoch, false) {
            PushStateReducer.beginRegistration(
                it.copy(registration = it.registration.copy(accountGeneration = owner.generation)),
                owner,
                fid,
                nonce,
                operation,
            )
        }.await()
        if (started.isFailure) return true
        val result = remote(
            RemoteGeneration(
                owner,
                ingressEpoch,
                operation,
                fid,
                registration.installationId,
                credentialVersion,
            ),
        ) {
            when (operation) {
                RegistrationOperation.CREATE ->
                    installationRepository.create(PushInstallationFid(fid))
                RegistrationOperation.ROTATE -> {
                    val installationId = registration.installationId
                    if (installationId == null) {
                        installationRepository.create(PushInstallationFid(fid))
                    } else {
                        installationRepository.update(
                            PushInstallationId(installationId),
                            PushInstallationFid(fid),
                        )
                    }
                }
                else -> Result.failure(IllegalStateException("Invalid registration operation"))
            }
        }.await()
        if (!isEpochAuthorized(ingressEpoch, allowActivation = true)) return true
        val latest = readState()
        val latestSession = authSessionRepository.read()
        val latestCredentialVersion = authSessionRepository.credentialState.first().credentialVersion
        val request = PushRegistrationRequestFence(
            owner,
            fid,
            operation,
            registration.installationId,
            nonce,
            latest.registration.terminalNonce,
            ingressEpoch,
            credentialVersion,
        )
        if (!request.matches(latestSession, latestCredentialVersion, latest)) return true
        val error = result.exceptionOrNull()
        val status = error.httpStatus()
        return if (result.isSuccess) {
            when (val upsert = result.getOrThrow()) {
                is PushInstallationUpsertResult.Acknowledged -> {
                    writeState(ingressEpoch, false) {
                        PushStateReducer.acknowledgeRegistration(
                            it,
                            owner,
                            nonce,
                            upsert.installation.installationId.value,
                        )
                    }.await()
                    maybeOpenActivatedOwner(owner)
                    true
                }
                is PushInstallationUpsertResult.Terminal -> {
                    writeState(ingressEpoch, false) {
                        PushStateReducer.recordTerminal(
                            it,
                            owner,
                            nonce,
                            RegistrationTerminal.MALFORMED_SUCCESS,
                        )
                    }.await()
                    true
                }
            }
        } else if (status == 404 && operation == RegistrationOperation.ROTATE) {
            writeState(ingressEpoch, false) {
                it.copy(
                    registration = it.registration.copy(
                        installationId = null,
                        operation = RegistrationOperation.CREATE,
                        retryAttempt = 0,
                    ),
                )
            }.await()
            false
        } else if (status == 401) {
            writeState(ingressEpoch, false) {
                PushStateReducer.recordBlockedAuth(
                    it,
                    owner,
                    nonce,
                    credentialVersion.epoch,
                    credentialVersion.revision,
                )
            }.await()
            true
        } else {
            val terminal = error.terminalStatus()
            if (terminal != null) {
                writeState(ingressEpoch, false) {
                    PushStateReducer.recordTerminal(it, owner, nonce, terminal)
                }.await()
                true
            } else if (isTransient(error)) {
                val attempt = latest.registration.retryAttempt + 1
                if (attempt >= MAX_ATTEMPTS) {
                    writeState(ingressEpoch, false) {
                        PushStateReducer.recordTerminal(
                            it,
                            owner,
                            nonce,
                            RegistrationTerminal.SUSPENDED_RETRY_EXHAUSTED,
                        )
                    }.await()
                    true
                } else {
                    writeState(ingressEpoch, false) {
                        PushStateReducer.recordRetry(it, owner, nonce)
                    }.await()
                    false
                }
            } else {
                writeState(ingressEpoch, false) {
                    PushStateReducer.recordTerminal(
                        it,
                        owner,
                        nonce,
                        RegistrationTerminal.PERMANENT_FAILURE,
                    )
                }.await()
                true
            }
        }
    }

    private suspend fun processDataMessage(
        data: Map<String, String>,
        hasNotificationBlock: Boolean,
        ingressEpoch: Long,
    ) {
        val reminder = MeetingReminderParser.parse(data, hasNotificationBlock) ?: return
        val session = authSessionRepository.read()
        val accepted = AtomicBoolean(false)
        val evicted = arrayListOf<String>()
        val written = writeState(ingressEpoch, false) { current ->
            val owner = current.registration.owner?.takeIf {
                session.stage != AuthSession.Stage.LoggedOut && it.userId == session.userId
            }
            when (
                val result = PushStateReducer.ingest(
                    current,
                    owner,
                    reminder.eventId.toString(),
                    reminder.meetingId,
                    reminder.reminderOffsetMinutes,
                    reminder.issuedAt.toEpochMilli(),
                    System.currentTimeMillis(),
                )
            ) {
                is LedgerIngressResult.Accepted -> {
                    accepted.set(true)
                    evicted += result.evictedDisplayedEventIds
                    result.state
                }
                else -> current
            }
        }.await().getOrNull() ?: return
        if (!accepted.get()) return
        if (evicted.isNotEmpty()) {
            val cancellation = submitEffect(
                ingressEpoch,
                LifecycleEffectKind.NOTIFICATION_CANCEL,
                action = {
                    presentation.cancel(evicted)
                },
                dedupeKey = "notification-cancel:${evicted.sorted().joinToString(",")}",
            )
            cancellation?.completion?.await()
        }
        val owner = written.registration.owner ?: return
        val event = written.ledger
            .filterIsInstance<LedgerRecord.OwnedReminderEvent>()
            .firstOrNull { it.eventId == reminder.eventId.toString() && it.owner == owner }
            ?: return
        val displayed = AtomicBoolean(false)
        val present = submitEffect(
            ingressEpoch,
            LifecycleEffectKind.PRESENT,
            action = {
                displayed.set(presentation.present(event))
            },
            dedupeKey = "present:${event.eventId}",
        ) ?: return
        if (present.completion.await().isSuccess && displayed.get()) {
            writeState(ingressEpoch, false) {
                PushStateReducer.markDisplayed(it, event.eventId, owner)
            }.await()
        }
    }

    private fun launchFirebase(
        kind: FirebaseKind,
        requestedEpoch: Long?,
        credentialVersion: CredentialVersion? = null,
    ): FirebaseHandle {
        val completion = CompletableDeferred<Result<Unit>>()
        val command = FirebaseCommand(
            id = ids.incrementAndGet(),
            kind = kind,
            epoch = requestedEpoch ?: synchronized(monitor) { epoch },
            credentialVersion = credentialVersion,
            completion = completion,
        )
        synchronized(monitor) {
            if (firebaseCommand != null &&
                firebaseCommand?.state != RemoteHandleState.COMPLETED
            ) {
                completion.complete(Result.failure(IllegalStateException("Firebase command already active")))
                return FirebaseHandle(command, completion)
            }
            if (kind == FirebaseKind.REGISTER && registerCallbackDebt > 0) {
                /*
                 * Firebase callbacks carry no command identity. Do not let a callback
                 * for a timed-out command become the callback for a later command.
                 * The debt is released only by quarantining one callback while no
                 * register command is active.
                 */
                completion.complete(
                    Result.failure(IllegalStateException("Firebase register callback debt outstanding")),
                )
                return FirebaseHandle(command, completion)
            }
            firebaseCommand = command
            if (kind == FirebaseKind.UNREGISTER) {
                callbackDebt = CompletableDeferred()
                callbackDebtSatisfied = false
                unregisterTaskSucceeded = false
                lastUnregisterCompletion = completion
            }
        }
        completionScope.launch {
            val started = synchronized(monitor) {
                if (command.stale ||
                    (kind == FirebaseKind.REGISTER && command.epoch != epoch)
                ) {
                    false
                } else {
                    command.state = RemoteHandleState.STARTED
                    true
                }
            }
            if (!started) {
                finishFirebase(command)
                completion.complete(Result.failure(IllegalStateException("Suppressed Firebase command")))
                return@launch
            }
            val task = runCatching {
                if (kind == FirebaseKind.REGISTER) {
                    fcm.register()
                } else {
                    synchronized(monitor) { unregisterInvocations++ }
                    fcm.unregister()
                }
            }.getOrElse {
                finishFirebase(command)
                completion.complete(Result.failure(it))
                return@launch
            }
            val taskResult = runCatching { task.await() }
                .getOrElse { Result.failure<Unit>(it) }
            if (taskResult.isFailure) {
                finishFirebase(command)
                completion.complete(taskResult)
                return@launch
            }
            if (kind == FirebaseKind.REGISTER) {
                val callback = withTimeoutOrNull(FIREBASE_CALLBACK_TIMEOUT_MILLIS) {
                    command.callback.await()
                }
                if (callback == null || command.fid == null) {
                    synchronized(monitor) {
                        registerCallbackDebt++
                    }
                    finishFirebase(command)
                    completion.complete(
                        Result.failure(IllegalStateException("Firebase register callback missing")),
                    )
                    return@launch
                }
            } else {
                synchronized(monitor) {
                    unregisterTaskSucceeded = true
                    if (callbackDebtSatisfied) command.callback.complete(Unit)
                }
            }
            finishFirebase(command)
            completion.complete(Result.success(Unit))
            launchDrainIfNeeded()
        }
        return FirebaseHandle(command, completion)
    }

    private fun finishFirebase(command: FirebaseCommand) {
        synchronized(monitor) {
            command.state = RemoteHandleState.COMPLETED
            if (firebaseCommand?.id == command.id) firebaseCommand = null
        }
    }

    private fun launchDrainIfNeeded() {
        val shouldLaunch = synchronized(monitor) {
            (phase == PushLifecyclePhase.DRAINING || phase == PushLifecyclePhase.DRAIN_BLOCKED) &&
                !drainStarted &&
                activeLeases == 0
        }
        if (shouldLaunch) launchDrain()
    }

    private fun launchDrain() {
        synchronized(monitor) {
            if (drainStarted || phase != PushLifecyclePhase.DRAINING) return
            drainStarted = true
        }
        completionScope.launch {
            val currentDrain = synchronized(monitor) { drainId }
            try {
                if (!isCurrentDrain(currentDrain)) return@launch
                awaitStaleOperations(currentDrain)
                if (!isCurrentDrain(currentDrain)) return@launch
                if (!confirmUnregistered(currentDrain)) return@launch
                if (!isCurrentDrain(currentDrain)) return@launch
                val scrub = scrubWithRetry(currentDrain) ?: return@launch
                if (!isCurrentDrain(currentDrain)) return@launch
                val events = synchronized(monitor) {
                    (scrub.eventIds + pendingNotificationCancellationIds).distinct()
                }
                if (!cancelSchedulerWithRetry(currentDrain)) return@launch
                if (!isCurrentDrain(currentDrain)) return@launch
                if (!cancelNotificationsWithRetry(currentDrain, events)) return@launch
                val current = readAccountForDrain(currentDrain) ?: return@launch
                val userId = current.userId
                    ?.takeIf { current.stage != AuthSession.Stage.LoggedOut }
                synchronized(monitor) {
                    if (phase != PushLifecyclePhase.DRAINING || activeLeases != 0) return@synchronized
                    if (userId == null) {
                        currentActivationUser = null
                        phase = PushLifecyclePhase.OPEN
                    } else {
                        currentActivationUser = userId
                        phase = PushLifecyclePhase.ACTIVATING_CURRENT
                    }
                    drainStarted = false
                    drainFailure = null
                }
                if (userId != null) enqueueScheduler(captureExitEpoch(), allowActivation = true)
            } catch (error: kotlinx.coroutines.CancellationException) {
                synchronized(monitor) {
                    if (drainId == currentDrain && phase == PushLifecyclePhase.DRAINING) {
                        drainStarted = false
                        drainFailure = DrainTicket(
                            currentDrain,
                            1,
                            DrainBlockReason.FINAL_SCRUB,
                        )
                        phase = PushLifecyclePhase.DRAIN_BLOCKED
                    }
                }
                throw error
            } catch (_: Throwable) {
                blockDrain(
                    DrainTicket(
                        currentDrain,
                        1,
                        DrainBlockReason.FINAL_SCRUB,
                    ),
                )
            } finally {
                synchronized(monitor) {
                    if (drainId == currentDrain && phase == PushLifecyclePhase.DRAINING) {
                        drainStarted = false
                    }
                }
            }
        }
    }

    private suspend fun awaitStaleOperations(drain: Long) {
        while (true) {
            if (!isCurrentDrain(drain)) return
            val pending = synchronized(monitor) {
                children.values.any { it.stale } ||
                    localWrites.values.any { it.stale } ||
                    remoteHandles.values.any {
                        it.state == RemoteHandleState.STARTED || it.state == RemoteHandleState.PENDING
                    } ||
                    firebaseCommand?.state == RemoteHandleState.STARTED ||
                    firebaseCommand?.state == RemoteHandleState.PENDING
            }
            if (!pending) return
            delay(1L)
        }
    }

    private suspend fun readAccountForDrain(drain: Long): AuthSession? {
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return authSessionRepository.read()
            } catch (_: Throwable) {
                if (attempt == MAX_ATTEMPTS - 1) {
                    blockDrain(
                        DrainTicket(drain, attempt + 1, DrainBlockReason.FINAL_SCRUB),
                    )
                    return null
                }
                delay(RETRY_DELAYS_MILLIS[attempt])
            }
        }
        return null
    }

    private fun isCurrentDrain(drain: Long): Boolean =
        synchronized(monitor) {
            drainId == drain && phase == PushLifecyclePhase.DRAINING && activeLeases == 0
        }

    private suspend fun confirmUnregistered(drain: Long): Boolean {
        val existing = synchronized(monitor) { lastUnregisterCompletion }
        if (existing != null && !existing.isCompleted) {
            runCatching { existing.await() }
        }
        if (synchronized(monitor) { unregisterTaskSucceeded }) {
            val callback = synchronized(monitor) { callbackDebt }
            if (synchronized(monitor) { callbackDebtSatisfied }) return true
            if (withTimeoutOrNull(FIREBASE_CALLBACK_TIMEOUT_MILLIS) { callback?.await() } != null) {
                return true
            }
        }
        val consumedAttempts = synchronized(monitor) {
            unregisterInvocations.coerceAtMost(MAX_ATTEMPTS)
        }
        val remainingAttempts = MAX_ATTEMPTS - consumedAttempts
        if (remainingAttempts == 0) {
            val step = if (synchronized(monitor) { unregisterTaskSucceeded }) {
                DrainBlockReason.UNREGISTER_CALLBACK
            } else {
                DrainBlockReason.UNREGISTER_TASK
            }
            blockDrain(DrainTicket(drain, MAX_ATTEMPTS, step))
            return false
        }
        repeat(remainingAttempts) { retryIndex ->
            val attempt = consumedAttempts + retryIndex + 1
            val ticket = DrainTicket(drain, attempt, DrainBlockReason.UNREGISTER_TASK)
            val command = launchFirebase(FirebaseKind.UNREGISTER, null)
            val taskResult = command.completion.await()
            if (taskResult.isSuccess) {
                val callback = synchronized(monitor) { callbackDebt }
                val satisfied = synchronized(monitor) { callbackDebtSatisfied }
                if (satisfied) return true
                val callbackArrived = withTimeoutOrNull(FIREBASE_CALLBACK_TIMEOUT_MILLIS) {
                    callback?.await()
                }
                if (callbackArrived != null) {
                    synchronized(monitor) { callbackDebtSatisfied = true }
                    return true
                }
                if (attempt == MAX_ATTEMPTS - 1) {
                    blockDrain(ticket.copy(step = DrainBlockReason.UNREGISTER_CALLBACK))
                    return false
                }
            } else if (attempt == MAX_ATTEMPTS - 1) {
                blockDrain(ticket)
                return false
            }
            delay(RETRY_DELAYS_MILLIS[attempt - 1])
        }
        return false
    }

    private suspend fun cancelSchedulerWithRetry(drain: Long): Boolean {
        repeat(MAX_ATTEMPTS) { attempt ->
            val effect = submitEffect(
                epoch = null,
                kind = LifecycleEffectKind.SCHEDULER_CANCEL,
                action = { workScheduler.cancel() },
                allowDuringExit = true,
                dedupeKey = "scheduler-cancel:$drain",
            )
            if (effect?.completion?.await()?.isSuccess == true) return true
            if (attempt == MAX_ATTEMPTS - 1) {
                blockDrain(DrainTicket(drain, attempt + 1, DrainBlockReason.SCHEDULER_CANCEL))
                return false
            }
            delay(RETRY_DELAYS_MILLIS[attempt])
        }
        return false
    }

    private suspend fun cancelNotificationsWithRetry(
        drain: Long,
        events: List<String>,
    ): Boolean {
        events.forEach { eventId ->
            var succeeded = false
            for (attempt in 0 until MAX_ATTEMPTS) {
                val effect = submitEffect(
                    epoch = null,
                    kind = LifecycleEffectKind.NOTIFICATION_CANCEL,
                    action = { presentation.cancel(listOf(eventId)) },
                    allowDuringExit = true,
                    dedupeKey = "notification-cancel:$eventId",
                )
                if (effect?.completion?.await()?.isSuccess == true) {
                    succeeded = true
                    synchronized(monitor) {
                        pendingNotificationCancellationIds.remove(eventId)
                    }
                    break
                }
                if (attempt == MAX_ATTEMPTS - 1) {
                    blockDrain(
                        DrainTicket(
                            drain,
                            attempt + 1,
                            DrainBlockReason.NOTIFICATION_CANCEL,
                            eventId,
                        ),
                    )
                    return false
                }
                delay(RETRY_DELAYS_MILLIS[attempt])
            }
            if (!succeeded) return false
        }
        return true
    }

    private fun blockDrain(ticket: DrainTicket) {
        synchronized(monitor) {
            if (ticket.drainId != drainId) return
            drainFailure = ticket
            drainBlockedCredentialVersion = lastObservedCredentialVersion
            phase = PushLifecyclePhase.DRAIN_BLOCKED
            drainStarted = false
        }
    }

    private data class ScrubResult(
        val success: Boolean,
        val installationId: String?,
        val eventIds: List<String>,
    )

    private suspend fun scrubWithRetry(drain: Long): ScrubResult? {
        repeat(MAX_ATTEMPTS) { attempt ->
            val result = try {
                finalScrub(System.currentTimeMillis(), false)
            } catch (_: Throwable) {
                ScrubResult(false, null, emptyList())
            }
            if (result.success) return result
            if (attempt == MAX_ATTEMPTS - 1) {
                blockDrain(
                    DrainTicket(drain, attempt + 1, DrainBlockReason.FINAL_SCRUB),
                )
                return null
            }
            delay(RETRY_DELAYS_MILLIS[attempt])
        }
        return null
    }

    private suspend fun finalScrub(
        now: Long,
        retainInstallationCleanup: Boolean,
    ): ScrubResult {
        val current = readState()
        val installationId = current.registration.installationId
        val owner = current.registration.owner
        val discarded = current.ledger
            .filterIsInstance<LedgerRecord.OwnedReminderEvent>()
            .filter { owner != null && it.owner == owner }
            .map { it.eventId }
        val scrubEpoch = synchronized(monitor) { epoch }
        val result = writeState(
            epoch = scrubEpoch,
            allowDuringExit = true,
            authorityEpoch = scrubEpoch,
        ) {
            val cleared = PushStateReducer.clearAccountScopedState(it, owner, now)
            if (retainInstallationCleanup && owner != null && installationId != null) {
                PushStateReducer.recordAccountCleanupFailure(
                    cleared,
                    owner,
                    installationId,
                    retryAttempt = 0,
                    terminal = RegistrationTerminal.NONE,
                )
            } else {
                cleared
            }
        }.await()
        if (result.isFailure) return ScrubResult(false, installationId, emptyList())
        val tombstoned = result
            .getOrThrow()
            .ledger
            .filterIsInstance<LedgerRecord.DedupeTombstone>()
            .map { it.eventId }
        val eventIds = (discarded + tombstoned).distinct()
        synchronized(monitor) {
            pendingNotificationCancellationIds += eventIds
        }
        return ScrubResult(true, installationId, eventIds)
    }

    private fun maybeOpenActivatedOwner(owner: OwnerSnapshot) {
        synchronized(monitor) {
            if (phase == PushLifecyclePhase.ACTIVATING_CURRENT &&
                currentActivationUser == owner.userId
            ) {
                phase = PushLifecyclePhase.OPEN
                currentActivationUser = null
            }
        }
    }

    private fun enqueueScheduler(epoch: Long, allowActivation: Boolean = false): EffectTicket? =
        submitEffect(
            epoch = epoch,
            kind = LifecycleEffectKind.SCHEDULER_ENQUEUE,
            action = { workScheduler.enqueue() },
            allowActivation = allowActivation,
            dedupeKey = "scheduler-enqueue:$epoch",
        )

    private fun submitEffect(
        epoch: Long?,
        kind: LifecycleEffectKind,
        main: Boolean = false,
        action: () -> Unit,
        allowDuringExit: Boolean = false,
        allowActivation: Boolean = false,
        dedupeKey: String? = null,
    ): EffectTicket? {
        val completion = CompletableDeferred<Result<Unit>>()
        val record = EffectRecord(
            id = ids.incrementAndGet(),
            epoch = epoch,
            kind = kind,
            main = main,
            action = action,
            completion = completion,
            allowDuringExit = allowDuringExit,
            allowActivation = allowActivation,
            dedupeKey = dedupeKey,
        )
        var start = false
        synchronized(monitor) {
            if (effects.size >= EFFECT_QUEUE_CAPACITY) {
                completion.complete(Result.failure(IllegalStateException("Lifecycle effect queue full")))
                return null
            }
            if (dedupeKey != null) {
                val existing = effects.firstOrNull { it.dedupeKey == dedupeKey }
                    ?: activeEffects[dedupeKey]
                if (existing != null) {
                    return EffectTicket(
                        existing.id,
                        existing.epoch,
                        existing.kind,
                        existing.completion,
                    )
                }
            }
            if (!allowDuringExit &&
                !isEpochAuthorizedLocked(epoch, allowActivation = allowActivation)
            ) {
                completion.complete(Result.failure(IllegalStateException("Stale lifecycle effect")))
                return null
            }
            effects.addLast(record)
            if (!effectDraining) {
                effectDraining = true
                start = true
            }
        }
        if (start) drainEffects()
        return EffectTicket(record.id, epoch, kind, completion)
    }

    private fun drainEffects() {
        completionScope.launch {
            while (true) {
                val next: EffectRecord? = synchronized(monitor) {
                    if (effects.isEmpty()) {
                        effectDraining = false
                        null
                    } else {
                        effects.removeFirst()
                    }
                }
                if (next == null) return@launch
                next.dedupeKey?.let { key ->
                    synchronized(monitor) { activeEffects[key] = next }
                }
                if (next.suppressed) {
                    next.completion.complete(Result.failure(IllegalStateException("Suppressed lifecycle effect")))
                    next.dedupeKey?.let { key ->
                        synchronized(monitor) { activeEffects.remove(key, next) }
                    }
                    continue
                }
                if (next.main) {
                    withContext(mainDispatcher) {
                        invokeEffect(next)
                    }
                } else {
                    invokeEffect(next)
                }
            }
        }
    }

    private fun invokeEffect(record: EffectRecord) {
        val initiallyAuthorized = synchronized(monitor) {
            if (record.allowDuringExit) {
                record.state == RemoteHandleState.PENDING && !record.suppressed
            } else if (record.epoch == null) {
                false
            } else {
                record.state == RemoteHandleState.PENDING &&
                    !record.suppressed &&
                    isEpochAuthorizedLocked(record.epoch, allowActivation = record.allowActivation)
            }
        }
        if (!initiallyAuthorized) {
            record.completion.complete(Result.failure(IllegalStateException("Suppressed lifecycle effect")))
            record.dedupeKey?.let { key ->
                synchronized(monitor) { activeEffects.remove(key, record) }
            }
            return
        }
        val result = runCatching {
            beforeEffectInvocation(record.kind)
            val started = synchronized(monitor) {
                val authorized = !record.suppressed &&
                    (
                        record.allowDuringExit ||
                            (
                                record.epoch != null &&
                                    isEpochAuthorizedLocked(
                                        record.epoch,
                                        allowActivation = record.allowActivation,
                                    )
                            )
                    )
                if (authorized) record.state = RemoteHandleState.STARTED
                authorized
            }
            check(started) { "Suppressed lifecycle effect" }
            record.action()
            Unit
        }
        record.completion.complete(result)
        record.dedupeKey?.let { key ->
            synchronized(monitor) { activeEffects.remove(key, record) }
        }
    }

    private fun admitIngress(
        ingressEpoch: Long,
        allowActivation: Boolean = false,
        job: Job? = null,
    ): IngressPermit? {
        synchronized(monitor) {
            if (!isEpochAuthorizedLocked(ingressEpoch, allowActivation)) return null
            val id = ids.incrementAndGet()
            children[id] = ChildRecord(id, ingressEpoch, job = job)
            return IngressPermit(id, ingressEpoch)
        }
    }

    private fun linkChild(permit: IngressPermit, job: Job) {
        synchronized(monitor) {
            children[permit.id]?.job = job
        }
    }

    private fun finishChild(permit: IngressPermit) {
        synchronized(monitor) {
            children.remove(permit.id)
        }
    }

    private fun lifecycleAllowsIngress(ingressEpoch: Long): Boolean =
        synchronized(monitor) { isEpochAuthorizedLocked(ingressEpoch, false) }

    private fun isEpochAuthorized(
        ingressEpoch: Long,
        allowActivation: Boolean,
    ): Boolean = synchronized(monitor) {
        isEpochAuthorizedLocked(ingressEpoch, allowActivation)
    }

    private fun isEpochAuthorizedLocked(
        ingressEpoch: Long?,
        allowActivation: Boolean,
    ): Boolean {
        if (ingressEpoch == null || ingressEpoch != epoch || activeLeases != 0) return false
        return phase == PushLifecyclePhase.OPEN ||
            (allowActivation && phase == PushLifecyclePhase.ACTIVATING_CURRENT)
    }

    private suspend fun readState(): PushStateV1 =
        storeMutex.withLock { stateStore.read() }

    private fun writeState(
        epoch: Long,
        allowDuringExit: Boolean,
        authorityEpoch: Long? = null,
        transform: (PushStateV1) -> PushStateV1,
    ): Deferred<Result<PushStateV1>> {
        val completion = CompletableDeferred<Result<PushStateV1>>()
        val record = LocalRecord(ids.incrementAndGet(), epoch, completion)
        synchronized(monitor) {
            if ((!allowDuringExit && !isEpochAuthorizedLocked(epoch, allowActivation = true)) ||
                (allowDuringExit && authorityEpoch != null && !isExitAuthorityLocked(authorityEpoch))
            ) {
                completion.complete(
                    Result.failure(IllegalStateException("Stale lifecycle write")),
                )
                return completion
            }
            localWrites[record.id] = record
        }
        completionScope.launch {
            val result = runCatching {
                storeMutex.withLock {
                    stateStore.update { current ->
                        val authorized = (
                            allowDuringExit &&
                                (
                                    authorityEpoch == null ||
                                        synchronized(monitor) { isExitAuthorityLocked(authorityEpoch) }
                                )
                        ) ||
                            synchronized(monitor) {
                                isEpochAuthorizedLocked(epoch, allowActivation = true)
                            }
                        if (authorized) transform(current) else current
                    }
                }
            }
            completion.complete(result)
            synchronized(monitor) {
                record.state = RemoteHandleState.COMPLETED
                localWrites.remove(record.id)
            }
        }
        return completion
    }

    private fun isExitAuthorityLocked(expectedEpoch: Long): Boolean =
        expectedEpoch == epoch &&
            (phase == PushLifecyclePhase.EXITING || phase == PushLifecyclePhase.DRAINING)

    private fun <T> remote(
        generation: RemoteGeneration,
        allowDuringExit: Boolean = false,
        operation: suspend () -> Result<T>,
    ): Deferred<Result<T>> {
        val completion = CompletableDeferred<Result<T>>()
        val record = RemoteRecord(
            id = ids.incrementAndGet(),
            generation = generation,
            completion = completion,
            allowDuringExit = allowDuringExit,
        )
        synchronized(monitor) {
            if (!allowDuringExit &&
                !isEpochAuthorizedLocked(generation.epoch, allowActivation = true)
            ) {
                record.state = RemoteHandleState.SUPPRESSED
                completion.complete(Result.failure(IllegalStateException("Stale remote operation")))
                return completion
            }
            remoteHandles[record.id] = record
        }
        completionScope.launch {
            synchronized(monitor) {
                val staleExitAuthority = record.allowDuringExit &&
                    (
                        record.generation.epoch != epoch ||
                            phase != PushLifecyclePhase.EXITING
                    )
                if (record.state == RemoteHandleState.SUPPRESSED || staleExitAuthority) {
                    record.state = RemoteHandleState.SUPPRESSED
                    completion.complete(Result.failure(IllegalStateException("Suppressed remote operation")))
                    remoteHandles.remove(record.id)
                    return@launch
                }
                record.state = RemoteHandleState.STARTED
            }
            val result = try {
                operation()
            } catch (error: Throwable) {
                Result.failure(error)
            }
            completion.complete(result)
            synchronized(monitor) {
                record.state = RemoteHandleState.COMPLETED
                remoteHandles.remove(record.id)
            }
            launchDrainIfNeeded()
        }
        return completion
    }

    private suspend fun recordAccountCleanupFailure(
        owner: OwnerSnapshot,
        installationId: String,
        result: Result<PushInstallationDeleteResult>,
    ) {
        val current = readState()
        val previous = current.accountCleanup
            ?.takeIf { it.owner == owner && it.installationId == installationId }
        val attempt = ((previous?.retryAttempt ?: 0) + 1).coerceAtMost(MAX_ATTEMPTS)
        val terminal = when {
            result.isSuccess && result.getOrNull() is PushInstallationDeleteResult.Terminal ->
                RegistrationTerminal.MALFORMED_SUCCESS
            result.exceptionOrNull().terminalStatus() != null ->
                result.exceptionOrNull().terminalStatus()!!
            attempt >= MAX_ATTEMPTS -> RegistrationTerminal.SUSPENDED_RETRY_EXHAUSTED
            else -> RegistrationTerminal.NONE
        }
        val authorityEpoch = synchronized(monitor) { epoch }
        writeState(authorityEpoch, true, authorityEpoch = authorityEpoch) {
            PushStateReducer.recordAccountCleanupFailure(
                it,
                owner,
                installationId,
                attempt,
                terminal,
            )
        }.await()
    }

    private fun pushStateReadOnlyResult(state: PushStateV1): Result<PushStateV1> =
        Result.success(state)

    private data class ChildRecord(
        val id: Long,
        val epoch: Long,
        var stale: Boolean = false,
        var job: Job? = null,
    )

    private data class LocalRecord(
        val id: Long,
        val epoch: Long,
        val completion: CompletableDeferred<Result<PushStateV1>>,
        var state: RemoteHandleState = RemoteHandleState.STARTED,
        var stale: Boolean = false,
    )

    private data class RemoteRecord(
        val id: Long,
        val generation: RemoteGeneration,
        val completion: CompletableDeferred<*>,
        val allowDuringExit: Boolean,
        var state: RemoteHandleState = RemoteHandleState.PENDING,
    )

    private data class EffectRecord(
        val id: Long,
        val epoch: Long?,
        val kind: LifecycleEffectKind,
        val main: Boolean,
        val action: () -> Unit,
        val completion: CompletableDeferred<Result<Unit>>,
        val allowDuringExit: Boolean,
        val allowActivation: Boolean,
        val dedupeKey: String?,
        var suppressed: Boolean = false,
        var state: RemoteHandleState = RemoteHandleState.PENDING,
    )

    private enum class FirebaseKind {
        REGISTER,
        UNREGISTER,
    }

    private data class FirebaseHandle(
        val command: FirebaseCommand,
        val completion: Deferred<Result<Unit>>,
    )

    private class FirebaseCommand(
        val id: Long,
        val kind: FirebaseKind,
        val epoch: Long,
        val credentialVersion: CredentialVersion?,
        val completion: CompletableDeferred<Result<Unit>>,
    ) {
        val callback = CompletableDeferred<Unit>()
        var state: RemoteHandleState = RemoteHandleState.PENDING
        var fid: String? = null
        var stale: Boolean = false
    }

    private fun CredentialVersion.isAdvancedFrom(registration: RegistrationState): Boolean {
        val blockedEpoch = registration.blockedCredentialEpoch ?: return true
        val blockedRevision = registration.blockedCredentialRevision ?: return true
        return epoch != blockedEpoch || revision > blockedRevision
    }

    private fun CredentialVersion.isAdvancedFrom(previous: CredentialVersion): Boolean =
        epoch != previous.epoch || revision > previous.revision

    private fun Throwable?.httpStatus(): Int? =
        when (this) {
            is ApiException.ServerError -> metadata.status
            is ApiException.UnauthorizedError -> metadata?.status
            else -> null
        }

    private fun Throwable?.terminalStatus(): RegistrationTerminal? =
        when (httpStatus()) {
            401 -> RegistrationTerminal.BLOCKED_AUTH
            403 -> RegistrationTerminal.FORBIDDEN
            409 -> RegistrationTerminal.CONFLICT_BLOCKED
            in 400..499 -> RegistrationTerminal.PERMANENT_FAILURE
            else -> null
        }

    private fun isTransient(error: Throwable?): Boolean =
        error is ApiException.NetworkError ||
            error.httpStatus() in setOf(408, 429) ||
            error.httpStatus()?.let { it in 500..599 } == true

    private fun Result<PushInstallationDeleteResult>.isAcknowledged(): Boolean =
        getOrNull() == PushInstallationDeleteResult.Acknowledged

    private companion object {
        const val MAX_ATTEMPTS = 6
        const val FIREBASE_CALLBACK_TIMEOUT_MILLIS = 2_000L
        const val EFFECT_QUEUE_CAPACITY = PUSH_LEDGER_CAPACITY + 16
        val RETRY_DELAYS_MILLIS = longArrayOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L)
    }
}

private fun defaultMainDispatcher(): CoroutineDispatcher =
    runCatching { Dispatchers.Main.immediate }.getOrDefault(Dispatchers.Unconfined)

internal data class PushRegistrationRequestFence(
    val owner: OwnerSnapshot,
    val pendingFid: String,
    val operation: RegistrationOperation,
    val installationId: String?,
    val nonce: Long,
    val terminalNonce: Long,
    val exitEpoch: Long = 0L,
    val credentialVersion: CredentialVersion? = null,
) {
    fun matches(
        session: AuthSession,
        credentialVersion: CredentialVersion,
        state: PushStateV1,
    ): Boolean {
        val registration = state.registration
        return session.stage != AuthSession.Stage.LoggedOut &&
            session.userId == owner.userId &&
            registration.owner == owner &&
            registration.accountGeneration == owner.generation &&
            registration.operation == operation &&
            registration.pendingFid == pendingFid &&
            registration.installationId == installationId &&
            registration.nonce == nonce &&
            registration.terminalNonce == terminalNonce &&
            (this.credentialVersion == null || this.credentialVersion == credentialVersion)
    }

    fun matches(
        session: AuthSession,
        state: PushStateV1,
    ): Boolean {
        val registration = state.registration
        return session.stage != AuthSession.Stage.LoggedOut &&
            session.userId == owner.userId &&
            registration.owner == owner &&
            registration.accountGeneration == owner.generation &&
            registration.operation == operation &&
            registration.pendingFid == pendingFid &&
            registration.installationId == installationId &&
            registration.nonce == nonce &&
            registration.terminalNonce == terminalNonce
    }
}
