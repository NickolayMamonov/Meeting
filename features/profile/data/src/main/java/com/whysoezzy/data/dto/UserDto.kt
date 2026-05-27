package com.whysoezzy.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class UserProfileDto(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("surname") val surname: String,
    @SerialName("email") val email: String? = null,
    @SerialName("phone") val phone: String? = null,
    @SerialName("city") val city: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("avatarUrl") val avatarUrl: String? = null,
    @SerialName("interests") val interests: List<TagDto> = emptyList(),
    @SerialName("socialMedias") val socialMedias: List<SocialMediaDto> = emptyList(),
    @SerialName("showCommunities") val showCommunities: Boolean = true,
    @SerialName("showMeetings") val showMeetings: Boolean = true,
    @SerialName("notificationsEnabled") val notificationsEnabled: Boolean = true,
)

@Serializable
internal data class SocialMediaDto(
    @SerialName("type") val type: String,
    @SerialName("url") val url: String,
)

@Serializable
internal data class UpdateUserDto(
    @SerialName("name") val name: String? = null,
    @SerialName("surname") val surname: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("city") val city: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("avatarUrl") val avatarUrl: String? = null,
    @SerialName("interestIds") val interestIds: List<Long>? = null,
    @SerialName("socialMedias") val socialMedias: List<SocialMediaDto>? = null,
    @SerialName("showCommunities") val showCommunities: Boolean? = null,
    @SerialName("showMeetings") val showMeetings: Boolean? = null,
    @SerialName("notificationsEnabled") val notificationsEnabled: Boolean? = null,
)
