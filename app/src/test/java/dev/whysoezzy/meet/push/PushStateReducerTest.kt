package dev.whysoezzy.meet.push

import com.whysoezzy.auth.domain.models.AuthSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushStateReducerTest {
    @Test
    fun `unregistered callback is a durable no-op`() {
        val state = PushStateV1(
            registration = RegistrationState(
                owner = OwnerSnapshot(7, 4),
                accountGeneration = 4,
                pendingFid = "opaque",
                nonce = 12,
            ),
        )
        assertEquals(state, PushStateReducer.onUnregistered(state))
    }

    @Test
    fun `all departing owner statuses become route-free tombstones`() {
        val owner = OwnerSnapshot(7, 4)
        val state = PushStateV1(
            registration = RegistrationState(owner = owner, accountGeneration = 4),
            ledger = OwnedEventStatus.values().mapIndexed { index, status ->
                LedgerRecord.OwnedReminderEvent(
                    eventId = "event-$index",
                    owner = owner,
                    meetingId = (100 + index).toLong(),
                    reminderOffsetMinutes = 60,
                    issuedAt = 1L,
                    receivedAt = 2L,
                    status = status,
                    statusChangedAt = 3L,
                )
            },
        )
        val next = PushStateReducer.clearAccountScopedState(state, owner, 4)
        assertTrue(next.ledger.all { it is LedgerRecord.DedupeTombstone })
        assertTrue(
            next.ledger.all {
                (it as LedgerRecord.DedupeTombstone).reason == TombstoneReason.DISCARDED_ACCOUNT_CHANGED &&
                    it.owner == owner
            },
        )
        assertEquals(0, next.registration.owner?.userId ?: 0)
    }

    @Test
    fun `duplicate and bounded ledger are handled without side effects`() {
        val owner = OwnerSnapshot(1, 1)
        val state = PushStateV1()
        val accepted = PushStateReducer.ingest(state, owner, "e", 1, 60, 1, 2)
        val duplicate = PushStateReducer.ingest(
            (accepted as LedgerIngressResult.Accepted).state,
            owner,
            "e",
            1,
            60,
            1,
            2,
        )
        assertTrue(duplicate is LedgerIngressResult.Duplicate)
    }

    @Test
    fun `aged displayed records expose notification ids when atomically evicted`() {
        val owner = OwnerSnapshot(1, 1)
        val now = PUSH_LEDGER_RETENTION_MILLIS + 100L
        val state = PushStateV1(
            ledger = buildList {
                add(
                    LedgerRecord.OwnedReminderEvent(
                        eventId = "0-displayed",
                        owner = owner,
                        meetingId = 1L,
                        reminderOffsetMinutes = 60,
                        issuedAt = 1L,
                        receivedAt = 1L,
                        status = OwnedEventStatus.DISPLAYED,
                        statusChangedAt = 0L,
                    ),
                )
                repeat(PUSH_LEDGER_CAPACITY - 1) { index ->
                    add(
                        LedgerRecord.DedupeTombstone(
                            eventId = "tombstone-$index",
                            reason = TombstoneReason.DISCARDED_NO_OWNER,
                            terminalAt = 0L,
                        ),
                    )
                }
            },
        )

        val result = PushStateReducer.ingest(
            state = state,
            owner = owner,
            eventId = "new-event",
            meetingId = 2L,
            reminderOffsetMinutes = 60,
            issuedAt = now,
            receivedAt = now,
        ) as LedgerIngressResult.Accepted

        assertEquals(listOf("0-displayed"), result.evictedDisplayedEventIds)
        assertEquals(PUSH_LEDGER_CAPACITY, result.state.ledger.size)
        assertTrue(result.state.ledger.none { it.eventId == "0-displayed" })
    }

    @Test
    fun `blocked auth records credential version and a later registration clears it`() {
        val owner = OwnerSnapshot(7, 4)
        val state = PushStateV1(
            registration = RegistrationState(owner = owner, nonce = 9),
        )

        val blocked = PushStateReducer.recordBlockedAuth(state, owner, 9, "epoch-a", 3)
        assertEquals(RegistrationTerminal.BLOCKED_AUTH, blocked.registration.terminal)
        assertEquals("epoch-a", blocked.registration.blockedCredentialEpoch)
        assertEquals(3L, blocked.registration.blockedCredentialRevision)

        val rearmed = PushStateReducer.beginRegistration(
            blocked,
            owner,
            "fid",
            10,
            RegistrationOperation.CREATE,
        )
        assertEquals(RegistrationTerminal.NONE, rearmed.registration.terminal)
        assertEquals(null, rearmed.registration.blockedCredentialEpoch)
        assertEquals(null, rearmed.registration.blockedCredentialRevision)
    }

    @Test
    fun `pending display remains pending until presentation succeeds`() {
        val owner = OwnerSnapshot(3, 1)
        val accepted = PushStateReducer.ingest(
            PushStateV1(),
            owner,
            "event",
            42,
            60,
            1,
            2,
        ) as LedgerIngressResult.Accepted
        val pending = accepted.state.ledger.single() as LedgerRecord.OwnedReminderEvent
        assertEquals(OwnedEventStatus.PENDING_DISPLAY, pending.status)

        val displayed = PushStateReducer.markDisplayed(accepted.state, "event", owner)
        assertEquals(
            OwnedEventStatus.DISPLAYED,
            (displayed.ledger.single() as LedgerRecord.OwnedReminderEvent).status,
        )
        val displayedState = PushStateReducer.markDisplayed(
            accepted.state,
            "event",
            owner,
            now = 3L,
        )
        val displayedAtThree =
            displayedState.ledger.single() as LedgerRecord.OwnedReminderEvent
        assertEquals(3L, displayedAtThree.statusChangedAt)

        val claimedState = PushStateReducer.claimNavigation(
            displayedState,
            "event",
            owner,
            now = 4L,
        )
        val claimed = claimedState.ledger.single() as LedgerRecord.OwnedReminderEvent
        assertEquals(OwnedEventStatus.NAVIGATION_CLAIMED, claimed.status)
        assertEquals(4L, claimed.statusChangedAt)

        val navigatedState = PushStateReducer.markNavigated(
            claimedState,
            "event",
            owner,
            now = 5L,
        )
        val navigated = navigatedState.ledger.single() as LedgerRecord.OwnedReminderEvent
        assertEquals(OwnedEventStatus.NAVIGATED, navigated.status)
        assertEquals(5L, navigated.statusChangedAt)
    }

    @Test
    fun `account changed tombstones reject invalid owner semantics`() {
        val rejected = runCatching {
            PushStateReducer.requireValid(
                PushStateV1(
                    ledger = listOf(
                        LedgerRecord.DedupeTombstone(
                            eventId = "550e8400-e29b-41d4-a716-446655440000",
                            reason = TombstoneReason.DISCARDED_ACCOUNT_CHANGED,
                            owner = OwnerSnapshot(userId = 0L, generation = -1L),
                            terminalAt = 1L,
                        ),
                    ),
                ),
            )
        }.exceptionOrNull()

        assertTrue(rejected is IllegalArgumentException)
    }

    @Test
    fun `same FID callback rearms a terminal registration`() {
        val owner = OwnerSnapshot(7, 4)
        val state = PushStateV1(
            registration = RegistrationState(
                owner = owner,
                accountGeneration = owner.generation,
                pendingFid = "opaque",
                nonce = 12,
                retryAttempt = 6,
                terminal = RegistrationTerminal.SUSPENDED_RETRY_EXHAUSTED,
                terminalNonce = 12,
            ),
        )

        val rearmed = PushStateReducer.stageFid(state, "opaque")

        assertEquals(RegistrationTerminal.NONE, rearmed.registration.terminal)
        assertEquals(13L, rearmed.registration.nonce)
        assertEquals(0, rearmed.registration.retryAttempt)
        assertEquals(0L, rearmed.registration.terminalNonce)
    }

    @Test
    fun `aggregate validation rejects noncanonical IDs and timestamps`() {
        val owner = OwnerSnapshot(7, 4)
        val event = LedgerRecord.OwnedReminderEvent(
            eventId = "event",
            owner = owner,
            meetingId = 42,
            reminderOffsetMinutes = 60,
            issuedAt = 10,
            receivedAt = 9,
            statusChangedAt = 8,
        )

        val rejected = runCatching {
            PushStateReducer.requireValid(
                PushStateV1(
                    registration = RegistrationState(
                        owner = owner,
                        installationId = "550E8400-E29B-41D4-A716-446655440000",
                    ),
                    ledger = listOf(event),
                ),
            )
        }.exceptionOrNull()
        assertTrue(rejected is IllegalArgumentException)
    }

    @Test
    fun `request fence rejects stale operation and nonce`() {
        val owner = OwnerSnapshot(7, 4)
        val fence = PushRegistrationRequestFence(
            owner = owner,
            pendingFid = "fid-a",
            operation = RegistrationOperation.ROTATE,
            installationId = "550e8400-e29b-41d4-a716-446655440000",
            nonce = 12,
            terminalNonce = 12,
        )
        val session = AuthSession(7, AuthSession.Stage.Ready)
        val matching = PushStateV1(
            registration = RegistrationState(
                owner = owner,
                accountGeneration = owner.generation,
                installationId = fence.installationId,
                pendingFid = fence.pendingFid,
                operation = fence.operation,
                nonce = fence.nonce,
                terminalNonce = fence.terminalNonce,
            ),
        )

        assertTrue(fence.matches(session, matching))
        assertFalse(
            fence.matches(
                session,
                matching.copy(
                    registration = matching.registration.copy(
                        operation = RegistrationOperation.CREATE,
                        nonce = 13,
                    ),
                ),
            ),
        )
    }
}
