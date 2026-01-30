package dev.whysoezzy.uikit.models

data class UIKitMeeting(
    val id: Long,
    val imageUrl: String,
    val title: String,
    val description: String,
    val time: Long,
    val address: UIKitAddress,
    val tags: List<UIKitMeetingTag>,
    val personHost: UIKitPersonHost,
    val communityHost: UIKitCommunityHost,
    val participants: List<UIKitPerson>,
    val meetingStatus: UIKitMeetingStatus,
    val isUserInParticipants: Boolean,
    val date: String,
    val capacity: Int
)
