package com.whysoezzy.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MeetingDto(
    @SerialName("id") val id: Long,
    @SerialName("image_url") val imageUrl: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
    @SerialName("meeting_time") val meetingTime: Long,
    @SerialName("meeting_date") val meetingDate: String,
    @SerialName("address") val address: MeetingAddressDto,
    @SerialName("tags") val tags: List<MeetingTagDto>,
    @SerialName("person_host") val personHost: PersonHostDto,
    @SerialName("community_host") val communityHost: CommunityHostDto,
    @SerialName("participants") val participants: List<PersonDto>,
    @SerialName("meeting_status") val meetingStatus: String,
    @SerialName("is_user_in_participants") val isUserInParticipants: Boolean,
    @SerialName("capacity") val capacity: Int?
)