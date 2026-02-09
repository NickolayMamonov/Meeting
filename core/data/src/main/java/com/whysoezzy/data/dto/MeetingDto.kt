package com.whysoezzy.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MeetingDto(
    @SerialName("id") val id: Long,
    @SerialName("imageUrl") val imageUrl: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
    @SerialName("time") val time: Long,
    @SerialName("date") val date: String,
    @SerialName("address") val address: MeetingAddressDto,
    @SerialName("tags") val tags: List<MeetingTagDto>,
    @SerialName("personHost") val personHost: PersonHostDto?,
    @SerialName("communityHost") val communityHost: CommunityHostDto?,
    @SerialName("participants") val participants: List<PersonDto>,
    @SerialName("meetingStatus") val meetingStatus: String,
    @SerialName("isUserInParticipants") val isUserInParticipants: Boolean,
    @SerialName("capacity") val capacity: Int?
)