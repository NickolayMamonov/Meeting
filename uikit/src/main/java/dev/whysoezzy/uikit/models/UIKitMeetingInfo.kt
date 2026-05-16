package dev.whysoezzy.uikit.models

data class UIKitMeetingInfo(
    val id: Long,
    val title: String,
    val imageUrl: String,
    val date: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val tags: List<UIKitMeetingTag>,
    val meetingStatus: UIKitMeetingStatus? = null
)