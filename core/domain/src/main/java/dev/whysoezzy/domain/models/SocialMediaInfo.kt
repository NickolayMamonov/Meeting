package dev.whysoezzy.domain.models

enum class SocialMediaType {
    TELEGRAM,
    HABR,
    LINKEDIN,
    GITHUB,
    INSTAGRAM,
    TWITTER
}

data class SocialMediaInfo(
    val type: SocialMediaType,
    val url: String,
    val username: String
)
