package dev.whysoezzy.meet.push

import java.time.Instant
import java.util.UUID

internal data class MeetingReminder(
    val eventId: UUID,
    val meetingId: Long,
    val reminderOffsetMinutes: Int,
    val issuedAt: Instant,
)

internal object MeetingReminderParser {
    private val keys = setOf(
        "eventType",
        "schemaVersion",
        "eventId",
        "meetingId",
        "reminderOffsetMinutes",
        "issuedAt",
        "destination",
    )

    fun parse(
        data: Map<String, String>,
        hasNotificationBlock: Boolean = false,
    ): MeetingReminder? {
        if (hasNotificationBlock || data.keys != keys) return null
        if (data.values.any { it.isBlank() || it != it.trim() }) return null
        if (data["eventType"] != "MEETING_REMINDER" ||
            data["schemaVersion"] != "1" ||
            data["destination"] != "MEETING_DETAILS"
        ) {
            return null
        }
        val eventId = data["eventId"]?.takeIf(::isCanonicalUuid)?.let(UUID::fromString) ?: return null
        val meetingText = data["meetingId"] ?: return null
        if (!meetingText.matches(Regex("[1-9][0-9]*"))) return null
        val meetingId = meetingText.toLongOrNull() ?: return null
        val offsetText = data["reminderOffsetMinutes"] ?: return null
        if (offsetText != "60" && offsetText != "1440") return null
        val offset = offsetText.toInt()
        val issuedText = data["issuedAt"] ?: return null
        if (!issuedText.endsWith("Z")) return null
        val issuedAt = runCatching { Instant.parse(issuedText) }.getOrNull() ?: return null
        if (issuedAt.toString() != issuedText) return null
        return MeetingReminder(eventId, meetingId, offset, issuedAt)
    }
}
