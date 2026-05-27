package com.whysoezzy.domain.models

data class Community(
    val id: Long,
    val name: String,
    val description: String,
    val imageUrl: String,
    val subscribersCount: Int,
    val isSubscribed: Boolean,
    val tags: List<Tag>,
)
