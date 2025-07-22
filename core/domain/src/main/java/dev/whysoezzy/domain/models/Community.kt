package dev.whysoezzy.domain.models

data class Community(
    val id: Long,
    val imageUrl: String,
    val title: String,
    val tags: List<MeetingTag>,
    val description: String,
    val subscribers: List<Person>,
    val activeMeetings: List<MeetingInfo>,
    val finishedMeetings: List<MeetingInfo>,

    )
