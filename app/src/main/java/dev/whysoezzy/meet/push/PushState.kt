package dev.whysoezzy.meet.push

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.max

internal const val PUSH_STATE_VERSION = 1
internal const val PUSH_LEDGER_CAPACITY = 512
internal const val PUSH_LEDGER_RETENTION_MILLIS = 35L * 24L * 60L * 60L * 1_000L

@Serializable
internal enum class PermissionState {
    NOT_ELIGIBLE,
    ELIGIBLE,
    REQUESTED,
    SUPPRESSED_CORRUPT,
}

@Serializable
internal enum class RegistrationOperation {
    NONE,
    CREATE,
    ROTATE,
    DELETE,
}

@Serializable
internal enum class RegistrationTerminal {
    NONE,
    BLOCKED_AUTH,
    FORBIDDEN,
    CONFLICT_BLOCKED,
    MALFORMED_SUCCESS,
    PERMANENT_FAILURE,
    SUSPENDED_RETRY_EXHAUSTED,
}

@Serializable
internal data class OwnerSnapshot(
    val userId: Long,
    val generation: Long,
)

@Serializable
internal data class RegistrationState(
    val owner: OwnerSnapshot? = null,
    val accountGeneration: Long = 0L,
    val installationId: String? = null,
    val pendingFid: String? = null,
    val operation: RegistrationOperation = RegistrationOperation.NONE,
    val nonce: Long = 0L,
    val retryAttempt: Int = 0,
    val blockedCredentialEpoch: String? = null,
    val blockedCredentialRevision: Long? = null,
    val terminal: RegistrationTerminal = RegistrationTerminal.NONE,
)

@Serializable
internal enum class OwnedEventStatus {
    PENDING_DISPLAY,
    DISPLAYED,
    NAVIGATION_CLAIMED,
    NAVIGATED,
}

@Serializable
internal enum class TombstoneReason {
    DISCARDED_NO_OWNER,
    DISCARDED_ACCOUNT_CHANGED,
}

@Serializable
internal sealed class LedgerRecord {
    abstract val eventId: String
    abstract val terminalAt: Long

    @Serializable
    @SerialName("owned")
    data class OwnedReminderEvent(
        override val eventId: String,
        val owner: OwnerSnapshot,
        val meetingId: Long,
        val reminderOffsetMinutes: Int,
        val issuedAt: Long,
        val receivedAt: Long,
        val status: OwnedEventStatus = OwnedEventStatus.PENDING_DISPLAY,
        val statusChangedAt: Long,
    ) : LedgerRecord() {
        override val terminalAt: Long
            get() = statusChangedAt
    }

    @Serializable
    @SerialName("tombstone")
    data class DedupeTombstone(
        override val eventId: String,
        val reason: TombstoneReason,
        val owner: OwnerSnapshot? = null,
        override val terminalAt: Long,
    ) : LedgerRecord()
}

@Serializable
internal data class InstallPolicyState(
    val permission: PermissionState = PermissionState.NOT_ELIGIBLE,
)

@Serializable
internal data class PushStateV1(
    val version: Int = PUSH_STATE_VERSION,
    val installPolicy: InstallPolicyState = InstallPolicyState(),
    val registration: RegistrationState = RegistrationState(),
    val ledger: List<LedgerRecord> = emptyList(),
)

internal sealed interface LedgerIngressResult {
    data class Accepted(
        val state: PushStateV1,
    ) : LedgerIngressResult

    data object Duplicate : LedgerIngressResult

    data object LedgerCapacityBlocked : LedgerIngressResult

    data object InvalidOwner : LedgerIngressResult
}

internal object PushStateReducer {
    fun onUnregistered(state: PushStateV1): PushStateV1 = state

    fun markPermissionEligible(state: PushStateV1): PushStateV1 =
        if (state.installPolicy.permission == PermissionState.NOT_ELIGIBLE) {
            state.copy(installPolicy = InstallPolicyState(PermissionState.ELIGIBLE))
        } else {
            state
        }

    fun markPermissionRequested(state: PushStateV1): PushStateV1 =
        if (state.installPolicy.permission == PermissionState.ELIGIBLE) {
            state.copy(installPolicy = InstallPolicyState(PermissionState.REQUESTED))
        } else {
            state
        }

    fun beginRegistration(
        state: PushStateV1,
        owner: OwnerSnapshot,
        fid: String,
        nonce: Long,
        operation: RegistrationOperation,
    ): PushStateV1 {
        require(fid.isNotBlank()) { "FID must be non-blank" }
        val next = state.registration.copy(
            owner = owner,
            pendingFid = fid,
            operation = operation,
            nonce = nonce,
            retryAttempt = 0,
            terminal = RegistrationTerminal.NONE,
        )
        return state.copy(registration = next)
    }

    fun acknowledgeRegistration(
        state: PushStateV1,
        owner: OwnerSnapshot,
        nonce: Long,
        installationId: String?,
    ): PushStateV1 =
        if (state.registration.owner == owner && state.registration.nonce == nonce) {
            state.copy(
                registration = state.registration.copy(
                    owner = owner,
                    installationId = installationId ?: state.registration.installationId,
                    pendingFid = null,
                    operation = RegistrationOperation.NONE,
                    retryAttempt = 0,
                    terminal = RegistrationTerminal.NONE,
                ),
            )
        } else {
            state
        }

    fun recordTerminal(
        state: PushStateV1,
        owner: OwnerSnapshot,
        nonce: Long,
        terminal: RegistrationTerminal,
    ): PushStateV1 =
        if (state.registration.owner == owner && state.registration.nonce == nonce) {
            state.copy(registration = state.registration.copy(terminal = terminal))
        } else {
            state
        }

    fun clearAccountScopedState(
        state: PushStateV1,
        departing: OwnerSnapshot?,
        now: Long,
    ): PushStateV1 {
        val oldOwner = departing ?: state.registration.owner
        val nextGeneration = max(
            state.registration.owner?.generation ?: 0L,
            oldOwner?.generation ?: 0L,
        ) + 1L
        val rewritten = state.ledger.map { record ->
            if (record is LedgerRecord.OwnedReminderEvent &&
                oldOwner != null &&
                record.owner == oldOwner
            ) {
                LedgerRecord.DedupeTombstone(
                    eventId = record.eventId,
                    reason = TombstoneReason.DISCARDED_ACCOUNT_CHANGED,
                    owner = record.owner,
                    terminalAt = now,
                )
            } else {
                record
            }
        }
        return state
            .copy(
                registration = RegistrationState(
                    owner = null,
                    accountGeneration = state.registration.accountGeneration + 1L,
                    nonce = state.registration.nonce + 1L,
                ),
                ledger = rewritten,
            ).let {
                if (nextGeneration < 0L) it else it
            }
    }

    fun ingest(
        state: PushStateV1,
        owner: OwnerSnapshot?,
        eventId: String,
        meetingId: Long,
        reminderOffsetMinutes: Int,
        issuedAt: Long,
        receivedAt: Long,
    ): LedgerIngressResult {
        if (state.ledger.any { it.eventId == eventId }) return LedgerIngressResult.Duplicate
        if (owner == null) {
            return insert(
                state,
                LedgerRecord.DedupeTombstone(
                    eventId = eventId,
                    reason = TombstoneReason.DISCARDED_NO_OWNER,
                    terminalAt = receivedAt,
                ),
                receivedAt,
            )
        }
        return insert(
            state,
            LedgerRecord.OwnedReminderEvent(
                eventId = eventId,
                owner = owner,
                meetingId = meetingId,
                reminderOffsetMinutes = reminderOffsetMinutes,
                issuedAt = issuedAt,
                receivedAt = receivedAt,
                statusChangedAt = receivedAt,
            ),
            receivedAt,
        )
    }

    fun claimNavigation(
        state: PushStateV1,
        eventId: String,
        owner: OwnerSnapshot,
    ): PushStateV1 {
        val index = state.ledger.indexOfFirst {
            it is LedgerRecord.OwnedReminderEvent &&
                it.eventId == eventId &&
                it.owner == owner &&
                it.status == OwnedEventStatus.DISPLAYED
        }
        if (index < 0) return state
        val event = state.ledger[index] as LedgerRecord.OwnedReminderEvent
        return state.copy(
            ledger = state.ledger.toMutableList().also {
                it[index] = event.copy(
                    status = OwnedEventStatus.NAVIGATION_CLAIMED,
                    statusChangedAt = event.statusChangedAt,
                )
            },
        )
    }

    fun markDisplayed(
        state: PushStateV1,
        eventId: String,
        owner: OwnerSnapshot,
    ): PushStateV1 =
        state.copy(
            ledger = state.ledger.map { record ->
                if (record is LedgerRecord.OwnedReminderEvent &&
                    record.eventId == eventId &&
                    record.owner == owner &&
                    record.status == OwnedEventStatus.PENDING_DISPLAY
                ) {
                    record.copy(status = OwnedEventStatus.DISPLAYED)
                } else {
                    record
                }
            },
        )

    fun markNavigated(
        state: PushStateV1,
        eventId: String,
        owner: OwnerSnapshot,
    ): PushStateV1 =
        state.copy(
            ledger = state.ledger.map { record ->
                if (record is LedgerRecord.OwnedReminderEvent &&
                    record.eventId == eventId &&
                    record.owner == owner &&
                    record.status == OwnedEventStatus.NAVIGATION_CLAIMED
                ) {
                    record.copy(status = OwnedEventStatus.NAVIGATED)
                } else {
                    record
                }
            },
        )

    private fun insert(
        state: PushStateV1,
        record: LedgerRecord,
        now: Long,
    ): LedgerIngressResult {
        val retained = state.ledger
            .filterNot { isEvictable(it, now) }
            .toMutableList()
        val evictable = state.ledger
            .filter { isEvictable(it, now) }
            .sortedWith(compareBy<LedgerRecord> { it.terminalAt }.thenBy { it.eventId })
            .toMutableList()
        while (retained.size + evictable.size >= PUSH_LEDGER_CAPACITY && evictable.isNotEmpty()) {
            evictable.removeAt(0)
        }
        if (retained.size + evictable.size >= PUSH_LEDGER_CAPACITY) {
            return LedgerIngressResult.LedgerCapacityBlocked
        }
        return LedgerIngressResult.Accepted(
            state.copy(ledger = retained + evictable + record),
        )
    }

    private fun isEvictable(record: LedgerRecord, now: Long): Boolean =
        record.terminalAt <= now - PUSH_LEDGER_RETENTION_MILLIS &&
            (
                record !is LedgerRecord.OwnedReminderEvent ||
                    record.status != OwnedEventStatus.PENDING_DISPLAY
            )
}

internal fun isCanonicalUuid(value: String): Boolean =
    runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
