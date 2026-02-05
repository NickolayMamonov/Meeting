package com.whysoezzy.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserInfoDto(
    val id: Long,
    val name: String,
    val surname: String,
    val avatarUrl: String,
    val bio: String,
    val role: String
)