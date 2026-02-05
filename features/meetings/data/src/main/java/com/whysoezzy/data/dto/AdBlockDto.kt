package com.whysoezzy.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AdBlockResponseDto(
    val type: String,  // "COMMUNITIES", "TEXT", "PEOPLE"
    val id: Long,
    val isActive: Boolean,
    val title: String,
    val description: String,

    // For COMMUNITIES type
    val communities: List<CommunityInfoDto>? = null,

    // For TEXT type
    val actionText: String? = null,
    val actionUrl: String? = null,

    // For PEOPLE type
    val users: List<UserInfoDto>? = null
)

@Serializable
sealed class AdBlockDto {
    abstract val id: Long
    abstract val isActive: Boolean

    @Serializable
    @SerialName("COMMUNITY")
    data class CommunityAdDto(
        override val id: Long,
        val communityId: Long,
        val communityName: String,
        val communityDescription: String,
        val communityImageUrl: String,
        val subscribersCount: Int = 0,
        override val isActive: Boolean = true
    ) : AdBlockDto()

    @Serializable
    @SerialName("TEXT")
    data class TextAdDto(
        override val id: Long,
        val title: String,
        val description: String,
        val actionText: String? = null,
        val actionUrl: String? = null,
        override val isActive: Boolean = true
    ) : AdBlockDto()

    @Serializable
    @SerialName("BANNER")
    data class BannerAdDto(
        override val id: Long,
        val title: String,
        val imageUrl: String,
        val actionUrl: String,
        val backgroundColor: String? = null,
        override val isActive: Boolean = true
    ) : AdBlockDto()
}