package dev.whysoezzy.data.remote.dto

import com.google.gson.annotations.SerializedName

data class UserDto(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("surname") val surname: String,
    @SerializedName("email") val email: String,
    @SerializedName("city") val city: String,
    @SerializedName("avatar") val avatar: String,
    @SerializedName("phone") val phone: String,
    @SerializedName("bio") val bio: String,
    @SerializedName("interests") val interests: List<TagDto>,
    @SerializedName("social_media") val socialMedia: Map<SocialMedia, String>,
    @SerializedName("subscribed_communities") val subscribedCommunities: List<CommunityInfoDto>,
    @SerializedName("participating_meetings") val participatingMeetings: List<MeetingInfoDto>,
)

enum class SocialMedia {
    TELEGRAM,
    HABR
}