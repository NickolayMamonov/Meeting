package com.whysoezzy.domain.models

data class SocialMediaInfo(
    val type: SocialMediaType,
    val url: String,
    val username: String
)

enum class SocialMediaType {
    TELEGRAM,
    HABR,
    LINKEDIN,
    GITHUB
}

