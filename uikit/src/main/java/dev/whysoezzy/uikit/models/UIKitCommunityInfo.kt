package dev.whysoezzy.uikit.models

import androidx.compose.runtime.Immutable

@Immutable
data class UIKitCommunityInfo(
    val id: Long,
    val title: String,
    val imageUrl: String,
    val isSubscribed: Boolean,
)
