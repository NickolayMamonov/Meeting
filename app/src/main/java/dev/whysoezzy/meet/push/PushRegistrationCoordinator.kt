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
import kotlinx.coroutines.withTimeoutOrNull

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
    private val reconciliationMutex = Mutex()
    private var accountExitInProgress = false
    private var scope: CoroutineScope? = null
    private var observationJob: Job? = null

    fun start() {
        if (observationJob?.isActive == true) return
        val newScope = CoroutineScope(SupervisorJob() + dispatcher)
        scope = newScope
        observationJob = newScope.launch {
            authSessionRepository.credentialState
                .distinctUntilChanged()
                .collectLatest { credentialState ->
                    if (stateMutex.withLock { accountExitInProgress }) {
                        return@collectLatest
                    }
                    val userId = credentialState.session.userId
                        .takeIf { credentialState.session.stage != AuthSession.Stage.LoggedOut }
                    val oldRegistration = stateMutex.withLock {
                        val state = stateStore.read()
                        state.registration.owner
                            ?.takeIf { userId == null || it.userId != userId }
                            ?.let { it to state.registration.installationId }
                    }
                    if (userId == null || oldRegistration != null) {
                        beginAccountExit()
                        try {
                            val oldInstallationId = oldRegistration?.second
                            if (userId != null && oldInstallationId != null) {
                                try {
                                    withTimeoutOrNull(1_250L) {
                                        deleteInstallation(oldInstallationId)
                                    }
                                } catch (error: Exception) {
                                    if (error is kotlinx.coroutines.CancellationException) throw error
                                }
                            }
                            clearAccountState()
                            if (userId == null) {
                                fcm.unregister()
                            }
                        } finally {
                            endAccountExit()
                        }
                    }
                    if (userId != null) {
                        val registrationCanRun = stateMutex.withLock {
                            val registration = stateStore.read().registration
                            registration.terminal == RegistrationTerminal.NONE ||
                                (
                                    registration.terminal == RegistrationTerminal.BLOCKED_AUTH &&
                                        credentialState.credentialVersion.isAdvancedFrom(registration)
                                )
                        }
                        if (registrationCanRun) workScheduler.enqueue()
                    }
                }
        }
    }

    fun close() {
        scope?.cancel()
        workScheduler.cancel()
        scope = null
        observationJob = null
    }

    internal suspend fun beginAccountExit() {
        stateMutex.withLock {
            accountExitInProgress = true
            workScheduler.cancel()
        }
    }

    internal suspend fun endAccountExit() {
        stateMutex.withLock {
            accountExitInProgress = false
        }
    }

    fun onRegistered(fid: String) {
        val currentScope = scope ?: return
        val validFid = try {
            PushInstallationFid(fid).value
        } catch (_: IllegalArgumentException) {
            return
        }
        currentScope.launch {
            val staged = stateMutex.withLock {
                if (accountExitInProgress) return@withLock false
                val before = stateStore.read()
                val after = stateStore.update { PushStateReducer.stageFid(it, validFid) }
                before != after
            }
            if (staged) workScheduler.enqueue()
        }
    }

    internal suspend fun reconcileCurrent(): Boolean {
        val credentialState = authSessionRepository.credentialState.first()
        val userId = credentialState.session.userId
            ?.takeIf { credentialState.session.stage != AuthSession.Stage.LoggedOut }
            ?: return true
        return reconcile(userId, credentialState.credentialVersion)
    }

    internal suspend fun claimTap(command: PushTapCommand): Boolean =
        stateMutex.withLock {
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
     * Firebase's callback has no installation identity. It is advisory only: it cannot
     * mutate durable registration state. The guarded wake-up reads the current session
     * under the same mutex as cleanup before replacing the current unique work.
     */
    fun onUnregistered() {
        val currentScope = scope ?: return
        currentScope.launch {
            stateMutex.withLock {
                val session = authSessionRepository.read()
                if (!accountExitInProgress && session.stage != AuthSession.Stage.LoggedOut) {
                    workScheduler.enqueue()
                }
            }
        }
    }

    fun unregisterFirebase() {
        fcm.unregister()
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
                    when (
                        val ingress = PushStateReducer.ingest(
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
                            accepted = true
                            ingress.state
                        }
                        LedgerIngressResult.Duplicate,
                        LedgerIngressResult.LedgerCapacityBlocked,
                        LedgerIngressResult.InvalidOwner,
                        -> current
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

    suspend fun clearAccountState(now: Long = System.currentTimeMillis()): String? =
        stateMutex.withLock {
            val current = stateStore.read()
            val installationId = current.registration.installationId
            val discarded = current.ledger
                .filterIsInstance<LedgerRecord.OwnedReminderEvent>()
                .filter { it.owner == current.registration.owner }
                .map { it.eventId }
            stateStore.update {
                PushStateReducer.clearAccountScopedState(it, it.registration.owner, now)
            }
            presentation.cancel(discarded)
            workScheduler.cancel()
            installationId
        }

    suspend fun deleteInstallation(
        installationId: String,
    ): Result<PushInstallationDeleteResult> {
        val id = PushInstallationId(installationId)
        return installationRepository.delete(id)
    }

    private suspend fun reconcile(
        userId: Long,
        credentialVersion: CredentialVersion,
    ): Boolean = reconciliationMutex.withLock {
        reconcileSerialized(userId, credentialVersion)
    }

    private suspend fun reconcileSerialized(
        userId: Long,
        credentialVersion: CredentialVersion,
    ): Boolean {
        val snapshot = stateMutex.withLock {
            if (accountExitInProgress) return@withLock null
            val session = authSessionRepository.read()
            val state = stateStore.read()
            if (session.stage == AuthSession.Stage.LoggedOut || session.userId != userId) {
                return@withLock null
            }
            var registration = state.registration
            val credentialRearm = registration.terminal == RegistrationTerminal.BLOCKED_AUTH &&
                credentialVersion.isAdvancedFrom(registration)
            if (registration.terminal != RegistrationTerminal.NONE && !credentialRearm) {
                return@withLock null
            }
            val owner = registration.owner?.takeIf { it.userId == userId }
                ?: OwnerSnapshot(userId, registration.accountGeneration + 1L)
            if (credentialRearm) {
                registration = stateStore
                    .update {
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
                    }.registration
            }
            if (registration.owner != owner ||
                registration.accountGeneration != owner.generation
            ) {
                registration = stateStore
                    .update {
                        it.copy(
                            registration = it.registration.copy(
                                owner = owner,
                                accountGeneration = owner.generation,
                                nonce = it.registration.nonce + 1L,
                                terminalNonce = 0L,
                            ),
                        )
                    }.registration
            }
            owner to registration.pendingFid
        } ?: return true

        val (owner, fid) = snapshot
        return if (fid == null) {
            ensureFirebaseRegistered(owner)
        } else {
            reconcileWithFid(fid, owner)
        }
    }

    private suspend fun ensureFirebaseRegistered(owner: OwnerSnapshot): Boolean {
        val request = stateMutex.withLock {
            val session = authSessionRepository.read()
            val state = stateStore.read()
            if (session.stage == AuthSession.Stage.LoggedOut || session.userId != owner.userId) {
                return@withLock null
            }
            val registration = state.registration
            FirebaseRegistrationRequest(
                owner = owner,
                pendingFid = registration.pendingFid,
                operation = registration.operation,
                installationId = registration.installationId,
                nonce = registration.nonce,
                terminalNonce = registration.terminalNonce,
                attempt = registration.firebaseRetryAttempt,
            )
        } ?: return true

        return try {
            fcm.register()
            stateMutex.withLock {
                val current = stateStore.read()
                if (isCurrentRequest(authSessionRepository.read(), current, request)) {
                    stateStore.update(PushStateReducer::resetFirebaseRetry)
                }
            }
            true
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            stateMutex.withLock {
                val current = stateStore.read()
                if (!isCurrentRequest(authSessionRepository.read(), current, request)) {
                    return@withLock true
                }
                if (request.attempt + 1 >= MAX_ATTEMPTS) {
                    stateStore.update {
                        PushStateReducer.recordTerminal(
                            it,
                            request.owner,
                            request.nonce,
                            RegistrationTerminal.SUSPENDED_RETRY_EXHAUSTED,
                        )
                    }
                    true
                } else {
                    stateStore.update {
                        it.copy(
                            registration = it.registration.copy(
                                firebaseRetryAttempt = request.attempt + 1,
                            ),
                        )
                    }
                    false
                }
            }
        }
    }

    private suspend fun reconcileWithFid(
        fid: String,
        owner: OwnerSnapshot,
    ): Boolean {
        val request = stateMutex.withLock {
            val session = authSessionRepository.read()
            val current = stateStore.read()
            if (session.stage == AuthSession.Stage.LoggedOut || session.userId != owner.userId) {
                return@withLock null
            }
            val registration = current.registration
            if (registration.pendingFid == fid &&
                registration.owner == owner &&
                registration.terminal != RegistrationTerminal.NONE
            ) {
                return@withLock null
            }
            val operation = if (registration.installationId == null) {
                RegistrationOperation.CREATE
            } else {
                RegistrationOperation.ROTATE
            }
            val sameRequest = registration.owner == owner &&
                registration.pendingFid == fid &&
                registration.operation == operation &&
                registration.terminal == RegistrationTerminal.NONE
            val nonce = if (sameRequest) registration.nonce else registration.nonce + 1L
            val installationId = registration.installationId
            if (!sameRequest) {
                stateStore.update {
                    PushStateReducer.beginRegistration(
                        it.copy(
                            registration = it.registration.copy(
                                accountGeneration = owner.generation,
                            ),
                        ),
                        owner,
                        fid,
                        nonce,
                        operation,
                    )
                }
            }
            RegistrationRequest(
                owner = owner,
                pendingFid = fid,
                operation = operation,
                installationId = installationId,
                nonce = nonce,
                terminalNonce = if (sameRequest) registration.terminalNonce else 0L,
            )
        } ?: return true

        val result = when (request.operation) {
            RegistrationOperation.CREATE ->
                installationRepository.create(PushInstallationFid(request.pendingFid))

            RegistrationOperation.ROTATE -> {
                val installationId = request.installationId
                if (installationId == null) {
                    installationRepository.create(PushInstallationFid(request.pendingFid))
                } else {
                    installationRepository.update(
                        PushInstallationId(installationId),
                        PushInstallationFid(request.pendingFid),
                    )
                }
            }

            RegistrationOperation.NONE,
            RegistrationOperation.DELETE,
            -> return true
        }

        return stateMutex.withLock {
            val session = authSessionRepository.read()
            val current = stateStore.read()
            if (!request.matches(session, current)) return@withLock true

            val error = result.exceptionOrNull()
            val status = error.httpStatus()
            if (result.isSuccess) {
                when (val upsert = result.getOrThrow()) {
                    is PushInstallationUpsertResult.Acknowledged -> {
                        stateStore.update {
                            PushStateReducer.acknowledgeRegistration(
                                it,
                                request.owner,
                                request.nonce,
                                upsert.installation.installationId.value,
                            )
                        }
                        true
                    }
                    is PushInstallationUpsertResult.Terminal -> {
                        stateStore.update {
                            PushStateReducer.recordTerminal(
                                it,
                                request.owner,
                                request.nonce,
                                RegistrationTerminal.MALFORMED_SUCCESS,
                            )
                        }
                        true
                    }
                }
            } else if (status == 404 && request.operation == RegistrationOperation.ROTATE) {
                stateStore.update {
                    it.copy(
                        registration = it.registration.copy(
                            installationId = null,
                            operation = RegistrationOperation.CREATE,
                            retryAttempt = 0,
                        ),
                    )
                }
                false
            } else if (status == 401) {
                val credentialVersion = authSessionRepository.credentialState.first().credentialVersion
                stateStore.update {
                    PushStateReducer.recordBlockedAuth(
                        it,
                        request.owner,
                        request.nonce,
                        credentialVersion.epoch,
                        credentialVersion.revision,
                    )
                }
                true
            } else {
                val terminal = error.terminalStatus()
                if (terminal != null) {
                    stateStore.update {
                        PushStateReducer.recordTerminal(
                            it,
                            request.owner,
                            request.nonce,
                            terminal,
                        )
                    }
                    true
                } else if (isTransient(error)) {
                    val nextAttempt = current.registration.retryAttempt + 1
                    if (nextAttempt >= MAX_ATTEMPTS) {
                        stateStore.update {
                            PushStateReducer.recordTerminal(
                                it,
                                request.owner,
                                request.nonce,
                                RegistrationTerminal.SUSPENDED_RETRY_EXHAUSTED,
                            )
                        }
                        true
                    } else {
                        stateStore.update {
                            PushStateReducer.recordRetry(
                                it,
                                request.owner,
                                request.nonce,
                            )
                        }
                        false
                    }
                } else {
                    stateStore.update {
                        PushStateReducer.recordTerminal(
                            it,
                            request.owner,
                            request.nonce,
                            RegistrationTerminal.PERMANENT_FAILURE,
                        )
                    }
                    true
                }
            }
        }
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

    private fun isCurrentRequest(
        session: AuthSession,
        state: PushStateV1,
        request: FirebaseRegistrationRequest,
    ): Boolean {
        val registration = state.registration
        return session.stage != AuthSession.Stage.LoggedOut &&
            session.userId == request.owner.userId &&
            registration.owner == request.owner &&
            registration.accountGeneration == request.owner.generation &&
            registration.operation == request.operation &&
            registration.pendingFid == request.pendingFid &&
            registration.installationId == request.installationId &&
            registration.nonce == request.nonce &&
            registration.terminalNonce == request.terminalNonce
    }

    private data class FirebaseRegistrationRequest(
        val owner: OwnerSnapshot,
        val pendingFid: String?,
        val operation: RegistrationOperation,
        val installationId: String?,
        val nonce: Long,
        val terminalNonce: Long,
        val attempt: Int,
    )

    private typealias RegistrationRequest = PushRegistrationRequestFence

    private companion object {
        const val MAX_ATTEMPTS = 6
    }
}

internal data class PushRegistrationRequestFence(
    val owner: OwnerSnapshot,
    val pendingFid: String,
    val operation: RegistrationOperation,
    val installationId: String?,
    val nonce: Long,
    val terminalNonce: Long,
) {
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
