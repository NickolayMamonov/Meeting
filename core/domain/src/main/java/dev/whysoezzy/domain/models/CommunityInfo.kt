package dev.whysoezzy.domain.models

data class CommunityInfo(
    val id: Long,
    val imageUrl: String,
    val title: String,
    val tags: List<MeetingTag>
)
