package com.whysoezzy.data.dto


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("surname") val surname: String,
    @SerialName("email") val email: String?,
    @SerialName("city") val city: String,
    @SerialName("avatar") val avatar: String?,
    @SerialName("phone") val phone: String,
    @SerialName("bio") val bio: String,
    @SerialName("interests") val interests: List<MeetingTagDto>,
    @SerialName("socialMedia") val socialMedia: Map<String, String>,
    @SerialName("subscribedCommunities") val subscribedCommunities: List<CommunityInfoDto>,
    @SerialName("participatingMeetings") val participatingMeetings: List<MeetingInfoDto>
)
