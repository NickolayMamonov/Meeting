package com.whysoezzy.domain.models

data class MeetingInfo(
    val id: Long,
    val title: String,
    val imageUrl: String,
    val time: Long = 0L,
    val tags: List<MeetingTag> = emptyList(),
    val address: String = "",
    val meetingStatus: MeetingStatus = MeetingStatus.ACTIVE,
)

enum class MeetingStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED,
    FULL,
    DRAFT,
}
