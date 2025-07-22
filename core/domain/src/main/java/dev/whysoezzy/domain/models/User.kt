package dev.whysoezzy.domain.models

data class User(
    val id: Long,
    val name: String,
    val surname: String,
    val phoneNumber: String,
    val imageUrl: String,
    val city: String,
    val description: String,
    val email: String,
    val interests: List<MeetingTag>,
    val socialMedias: Map<SocialMedia, String>,
    val myMeetings: List<MeetingInfo>,
    val myCommunities: List<CommunityInfo>,

    )

enum class SocialMedia {
    TELEGRAM,
    HABR
}
