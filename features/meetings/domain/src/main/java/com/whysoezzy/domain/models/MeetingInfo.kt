package com.whysoezzy.domain.models

data class MeetingInfo(
    val id: Long,
    val title: String,
    val imageUrl: String,
    val time: Long,
    val tags: List<MeetingTag>,
    val address: String,
    val meetingStatus: MeetingStatus
)