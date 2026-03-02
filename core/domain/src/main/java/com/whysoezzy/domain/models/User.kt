package com.whysoezzy.domain.models

data class User(
    val id: Long,
    val name: String,
    val surname: String,
    val email: String,
    val city: String,
    val avatar: String,
    val phone: String,
    val bio: String,
    val socialMedias: List<SocialMediaInfo> = emptyList(),
    val interests: List<Tag> = emptyList(),
    val showCommunities: Boolean = true,
    val showMeetings: Boolean = true,
    val notificationsEnabled: Boolean = true
)
