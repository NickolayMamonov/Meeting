package dev.whysoezzy.meet.push

import org.junit.Assert.assertEquals
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
    }
}
