package dev.whysoezzy.meet.push

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MeetingReminderParserTest {
    @Test
    fun `accepts exact v1 reminder fixture`() {
        assertNotNull(MeetingReminderParser.parse(fixture()))
        assertNotNull(MeetingReminderParser.parse(fixture(1440)))
    }

    @Test
    fun `rejects unknown and missing keys`() {
        assertNull(MeetingReminderParser.parse(fixture() - "destination"))
        assertNull(MeetingReminderParser.parse(fixture() + ("extra" to "x")))
        assertNull(MeetingReminderParser.parse(fixture(), hasNotificationBlock = true))
    }

    @Test
    fun `rejects noncanonical values`() {
        assertNull(MeetingReminderParser.parse(fixture() + ("meetingId" to "01")))
        assertNull(MeetingReminderParser.parse(fixture() + ("reminderOffsetMinutes" to "60 ")))
        assertNull(MeetingReminderParser.parse(fixture() + ("eventId" to "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA")))
        assertNull(MeetingReminderParser.parse(fixture() + ("issuedAt" to "2026-08-15T12:00:00+00:00")))
    }

    private fun fixture(offset: Int = 60): Map<String, String> = mapOf(
        "eventType" to "MEETING_REMINDER",
        "schemaVersion" to "1",
        "eventId" to "01234567-89ab-cdef-0123-456789abcdef",
        "meetingId" to "42",
        "reminderOffsetMinutes" to offset.toString(),
        "issuedAt" to "2026-08-15T12:00:00Z",
        "destination" to "MEETING_DETAILS",
    )
}
