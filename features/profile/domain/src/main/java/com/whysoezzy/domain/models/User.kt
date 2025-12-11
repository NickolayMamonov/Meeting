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
    val interests: List<Tag>,
    val socialMedia: Map<SocialMedia, String>,
    val subscribedCommunities: List<CommunityInfo>,
    val participatingMeetings: List<MeetingInfo>
)
