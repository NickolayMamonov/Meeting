package dev.whysoezzy.domain.models

data class CommunityInfo(
    val id: Long,
    val imageUrl: String,
    val title: String,
    val description: String,
    val tags: List<MeetingTag> = emptyList(),
    val membersCount: Int = 0
)
