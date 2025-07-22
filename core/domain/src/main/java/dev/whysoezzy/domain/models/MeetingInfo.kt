package dev.whysoezzy.domain.models

data class MeetingInfo(
    val id: Long,
    val imageUrl: String,
    val title: String,
    val address: String,
    val tags: List<MeetingTag>,
    val time: Long,
    val meetingStatus: MeetingStatus
)
