package dev.whysoezzy.data.remote.dto

import com.google.gson.annotations.SerializedName

data class MeetingDto(
    @SerializedName("id") val id: Long,
    @SerializedName("image_url") val imageUrl: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("time") val time: Long,
    @SerializedName("date") val date: String,
    @SerializedName("address") val address: MeetingAddressDto,
    @SerializedName("tags") val tags: List<MeetingTagDto>,
    @SerializedName("person_host") val personHost: PersonHostDto,
    @SerializedName("community_host") val communityHost: CommunityHostDto,
    @SerializedName("participants") val participants: List<PersonDto>,
    @SerializedName("meeting_status") val meetingStatus: String,
    @SerializedName("is_user_in_participants") val isUserInParticipants: Boolean,
    @SerializedName("capacity") val capacity: Int
)