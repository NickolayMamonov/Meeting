package dev.whysoezzy.uikit.models

enum class UIKitSocialMedia {
    TELEGRAM,
    HABR,
    GITHUB,
    LINKEDIN
}

data class UIKitSocialMediaInfo(
    val type: UIKitSocialMedia,
    val url: String,
    val username: String
)