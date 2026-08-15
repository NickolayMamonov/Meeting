package dev.whysoezzy.meet.push

import com.whysoezzy.auth.domain.models.AuthSession
import com.whysoezzy.auth.domain.models.CredentialVersion
import com.whysoezzy.auth.domain.repository.AuthSessionRepository
import com.whysoezzy.domain.models.PushInstallationFid
import com.whysoezzy.domain.models.PushInstallationUpsertResult
import com.whysoezzy.domain.repository.PushInstallationRepository
import com.whysoezzy.network.error.ApiException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class PushRegistrationCoordinator(
    private val authSessionRepository: AuthSessionRepository,
    private val installationRepository: PushInstallationRepository,
    private val fcm: FcmRegistrationClient,
    private val stateStore: PushStateStore,
    private val presentation: ReminderPresentationGateway = NoOpReminderPresentationGateway,
    private val workScheduler: PushWorkScheduler = NoOpPushWorkScheduler,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val stateMutex = Mutex()
    private var scope: CoroutineScope? = null
    private var observationJob: Job? = null
    private var reconciliationJob: Job? = null

    fun start() {
        if (observationJob?.isActive == true) return
        val newScope = CoroutineScope(SupervisorJob() + dispatcher)
        scope = newScope
        observationJob = newScope.launch {
            authSessionRepository.credentialState
                .distinctUntilChanged()
                .collectLatest { credentialState ->
                    val userId = credentialState.session.userId
                        .takeIf { credentialState.session.stage != AuthSession.Stage.LoggedOut }
                    if (userId == null) {
                        clearCurrentOwner()
                    } else {
                        val hasDifferentOwner = stateMutex.withLock {
                            stateStore.read().registration.owner
                                ?.let { it.userId != userId }
                                ?: false
                        }
                        if (hasDifferentOwner) clearAccountState()
                        reconcile(userId, credentialState.credentialVersion)
                    }
                }
        }
    }

    fun close() {
        scope?.cancel()
        workScheduler.cancel()
        scope = null
        observationJob = null
        reconciliationJob = null
    }

    fun onRegistered(fid: String) {
        val currentScope = scope ?: return
        workScheduler.enqueue()
        currentScope.launch { reconcileWithFid(fid.trim()) }
    }

    internal suspend fun reconcileCurrent(): Boolean {
        val credentialState = authSessionRepository.credentialState.first()
        val userId = credentialState.session.userId ?: return true
        if (credentialState.session.stage == AuthSession.Stage.LoggedOut) return true
        return reconcile(userId, credentialState.credentialVersion)
    }

    internal suspend fun claimTap(command: PushTapCommand): Boolean {
        return stateMutex.withLock {
            val session = authSessionRepository.read()
            val state = stateStore.read()
            val owner = state.registration.owner
                ?.takeIf { session.userId != null && it.userId == session.userId }
                ?: return@withLock false
            val event = state.ledger
                .filterIsInstance<LedgerRecord.OwnedReminderEvent>()
                .firstOrNull {
                    it.eventId == command.eventId &&
                        it.meetingId == command.meetingId &&
                        it.owner == owner &&
                        it.status == OwnedEventStatus.DISPLAYED
                } ?: return@withLock false
            val next = stateStore.update {
                PushStateReducer.claimNavigation(it, event.eventId, owner)
            }
            next.ledger
                .filterIsInstance<LedgerRecord.OwnedReminderEvent>()
                .any {
                    it.eventId == event.eventId &&
                        it.status == OwnedEventStatus.NAVIGATION_CLAIMED &&
                        it.owner == owner
                }
        }
    }

    internal suspend fun completeTap(command: PushTapCommand) {
        stateMutex.withLock {
            val session = authSessionRepository.read()
            val state = stateStore.read()
            val owner = state.registration.owner
                ?.takeIf { session.userId != null && it.userId == session.userId }
                ?: return@withLock
            stateStore.update {
                PushStateReducer.markNavigated(it, command.eventId, owner)
            }
        }
    }

    /**
     * Firebase does not return an identity with this callback. It can never clear or
     * acknowledge durable state; it only wakes a guarded reconciliation from current state.
     */
    fun onUnregistered() {
        val currentScope = scope ?: return
        workScheduler.enqueue()
        reconciliationJob?.cancel()
        reconciliationJob = currentScope.launch {
            val session = authSessionRepository.read()
            if (session.stage != AuthSession.Stage.LoggedOut) {
                val userId = session.userId
                if (userId != null) reconcile(userId, authSessionRepository.credentialState.first().credentialVersion)
            }
        }
    }

    fun onDataMessage(
        data: Map<String, String>,
        hasNotificationBlock: Boolean,
    ) {
        val currentScope = scope ?: return
        currentScope.launch {
            val reminder = MeetingReminderParser.parse(data, hasNotificationBlock) ?: return@launch
            stateMutex.withLock {
                val session = authSessionRepository.read()
                val state = stateStore.read()
                val owner = state.registration.owner
                    ?.takeIf { session.stage != AuthSession.Stage.LoggedOut && it.userId == session.userId }
                var accepted = false
                val next = stateStore.update { current ->
                    val ingress = PushStateReducer.ingest(
                        current,
                        owner,
                        reminder.eventId.toString(),
                        reminder.meetingId,
                        reminder.reminderOffsetMinutes,
                        reminder.issuedAt.toEpochMilli(),
                        System.currentTimeMillis(),
                    )
                    when (ingress) {
                        is LedgerIngressResult.Accepted -> {
                            accepted = true
                            ingress.state
                        }

                        LedgerIngressResult.Duplicate,
                        LedgerIngressResult.LedgerCapacityBlocked,
                        -> current
                        else -> current
                    }
                }
                val owned = next.ledger
                    .filterIsInstance<LedgerRecord.OwnedReminderEvent>()
                    .firstOrNull { it.eventId == reminder.eventId.toString() }
                if (accepted && owned != null && owned.owner == owner && presentation.present(owned)) {
                    stateStore.update {
                        PushStateReducer.markDisplayed(it, owned.eventId, owned.owner)
                    }
                }
            }
        }
    }

    internal suspend fun drainPendingDisplays() {
        stateMutex.withLock {
            drainPendingDisplaysLocked()
        }
    }

    suspend fun clearAccountState(now: Long = System.currentTimeMillis()) {
        stateMutex.withLock {
            val current = stateStore.read()
            val installationId = current.registration.installationId
            var discarded = emptyList<String>()
            if (installationId != null) {
                runCatching {
                    installationRepository.delete(
                        com.whysoezzy.domain.models.PushInstallationId(installationId),
                    )
                }
            }
            stateStore.update { state ->
                discarded = state.ledger
                    .filterIsInstance<LedgerRecord.OwnedReminderEvent>()
                    .filter { it.owner == state.registration.owner }
                    .map { it.eventId }
                PushStateReducer.clearAccountScopedState(state, state.registration.owner, now)
            }
            presentation.cancel(discarded)
            workScheduler.cancel()
        }
    }

    private suspend fun reconcile(
        userId: Long,
        credentialVersion: CredentialVersion,
    ): Boolean {
        val state = stateMutex.withLock { stateStore.read() }
        val registration = state.registration
        if (registration.owner?.userId == userId &&
            registration.terminal == RegistrationTerminal.BLOCKED_AUTH &&
            !credentialVersion.isAdvancedFrom(registration)
        ) {
            return true
        }
        val currentOwner = state.registration.owner
        val owner = if (currentOwner?.userId == userId) {
            currentOwner
        } else {
            OwnerSnapshot(
                userId = userId,
                generation = state.registration.accountGeneration + 1L,
            )
        }
        val fid = state.registration.pendingFid
        if (fid == null) {
            runCatching { fcm.register() }
            return true
        }
        return reconcileWithFid(fid, owner)
    }

    private suspend fun reconcileWithFid(fid: String) {
        if (fid.isBlank()) return
        val credentialState = authSessionRepository.credentialState.first()
        val session = credentialState.session
        if (session.stage == AuthSession.Stage.LoggedOut) return
        val state = stateMutex.withLock { stateStore.read() }
        val userId = session.userId ?: return
        val owner = state.registration.owner?.takeIf { it.userId == userId }
            ?: OwnerSnapshot(userId, state.registration.accountGeneration + 1L)
        reconcileWithFid(fid, owner)
    }

    private suspend fun reconcileWithFid(fid: String, owner: OwnerSnapshot): Boolean {
        if (fid.isBlank()) return true
        stateMutex.withLock {
            val current = stateStore.read()
            var operation = if (current.registration.installationId == null) {
                RegistrationOperation.CREATE
            } else {
                RegistrationOperation.ROTATE
            }
            val nonce = current.registration.nonce + 1L
            stateStore.update {
                PushStateReducer.beginRegistration(
                    it.copy(
                        registration = it.registration.copy(accountGeneration = owner.generation),
                    ),
                    owner,
                    fid,
                    nonce,
                    operation,
                )
            }
            var attempt = 0
            while (attempt < MAX_ATTEMPTS) {
                attempt++
                val result: Result<PushInstallationUpsertResult> = try {
                    if (operation == RegistrationOperation.CREATE) {
                        installationRepository.create(PushInstallationFid(fid))
                    } else {
                        val installationId = stateStore.read().registration.installationId
                        if (installationId == null) {
                            installationRepository.create(PushInstallationFid(fid))
                        } else {
                            installationRepository.update(
                                com.whysoezzy.domain.models
                                    .PushInstallationId(installationId),
                                PushInstallationFid(fid),
                            )
                        }
                    }
                } catch (error: Throwable) {
                    Result.failure(error)
                }
                if (result.isSuccess) {
                    val upsert = result.getOrNull()
                    if (upsert is PushInstallationUpsertResult.Acknowledged) {
                        stateStore.update {
                            PushStateReducer.acknowledgeRegistration(
                                it,
                                owner,
                                nonce,
                                upsert.installation.installationId.value,
                            )
                        }
                        return true
                    }
                    if (upsert is PushInstallationUpsertResult.Terminal) {
                        stateStore.update {
                            PushStateReducer.recordTerminal(
                                it,
                                owner,
                                nonce,
                                RegistrationTerminal.MALFORMED_SUCCESS,
                            )
                        }
                        return true
                    }
                }
                val error = result.exceptionOrNull()
                val status = error.httpStatus()
                if (status == 404 && operation == RegistrationOperation.ROTATE) {
                    stateStore.update {
                        it.copy(
                            registration = it.registration.copy(
                                installationId = null,
                                operation = RegistrationOperation.CREATE,
                            ),
                        )
                    }
                    operation = RegistrationOperation.CREATE
                    continue
                }
                if (status == 401) {
                    val credentialVersion = authSessionRepository.credentialState.first().credentialVersion
                    stateStore.update {
                        PushStateReducer.recordBlockedAuth(
                            it,
                            owner,
                            nonce,
                            credentialVersion.epoch,
                            credentialVersion.revision,
                        )
                    }
                    return true
                }
                val terminal = error.terminalStatus()
                if (terminal != null) {
                    stateStore.update {
                        PushStateReducer.recordTerminal(
                            it,
                            owner,
                            nonce,
                            terminal,
                        )
                    }
                    return true
                }
                if (!isTransient(error)) return true
            }
            stateStore.update {
                PushStateReducer.recordTerminal(
                    it,
                    owner,
                    nonce,
                    RegistrationTerminal.SUSPENDED_RETRY_EXHAUSTED,
                )
            }
            return false
        }
    }

    private suspend fun clearCurrentOwner() {
        clearAccountState()
        // Deliberately not awaited: the durable generation boundary makes this callback harmless.
        runCatching { fcm.unregister() }
    }

    private suspend fun drainPendingDisplaysLocked() {
        val session = authSessionRepository.read()
        val state = stateStore.read()
        val owner = state.registration.owner
            ?.takeIf { session.stage != AuthSession.Stage.LoggedOut && it.userId == session.userId }
            ?: return
        state.ledger
            .filterIsInstance<LedgerRecord.OwnedReminderEvent>()
            .filter { it.owner == owner && it.status == OwnedEventStatus.PENDING_DISPLAY }
            .forEach { event ->
                if (presentation.present(event)) {
                    stateStore.update {
                        PushStateReducer.markDisplayed(it, event.eventId, event.owner)
                    }
                }
            }
    }

    private fun CredentialVersion.isAdvancedFrom(registration: RegistrationState): Boolean {
        val blockedEpoch = registration.blockedCredentialEpoch ?: return true
        val blockedRevision = registration.blockedCredentialRevision ?: return true
        return epoch != blockedEpoch || revision > blockedRevision
    }

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

    private companion object {
        const val MAX_ATTEMPTS = 6
    }
}
