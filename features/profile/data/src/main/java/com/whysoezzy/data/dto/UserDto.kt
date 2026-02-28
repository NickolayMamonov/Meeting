package com.whysoezzy.data.dto


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfileDto(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("surname") val surname: String,
    @SerialName("email") val email: String?,
    @SerialName("phone") val phone: String?,
    @SerialName("city") val city: String?,
    @SerialName("description") val description: String?,
    @SerialName("avatarUrl") val avatarUrl: String?,
    @SerialName("socialMedias") val socialMedias: List<SocialMediaDto> = emptyList()
)

@Serializable
data class SocialMediaDto(
    @SerialName("type") val type: String,
    @SerialName("url") val url: String
)

@Serializable
data class UpdateUserDto(
    @SerialName("name") val name: String?,
    @SerialName("surname") val surname: String?,
    @SerialName("email") val email: String?,
    @SerialName("city") val city: String?,
    @SerialName("description") val description: String?,
    @SerialName("avatarUrl") val avatarUrl: String?
)
