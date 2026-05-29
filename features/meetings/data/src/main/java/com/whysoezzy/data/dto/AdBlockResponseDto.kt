package com.whysoezzy.data.dto

import kotlinx.serialization.Serializable

@Serializable
internal data class AdBlockResponseDto(
    val type: String, // "COMMUNITIES", "TEXT", "PEOPLE"
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
    val users: List<UserInfoDto>? = null,
)
