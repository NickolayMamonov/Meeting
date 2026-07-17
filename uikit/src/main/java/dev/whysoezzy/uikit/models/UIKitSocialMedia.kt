package dev.whysoezzy.uikit.models

import androidx.compose.runtime.Immutable

enum class UIKitSocialMedia {
    TELEGRAM,
    HABR,
}

@Immutable
data class UIKitSocialMediaInfo(
    val type: UIKitSocialMedia,
    val url: String,
    val username: String,
)
