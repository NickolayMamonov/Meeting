package dev.whysoezzy.uikit.models

import androidx.compose.runtime.Immutable

@Immutable
data class UIKitCommunity(
    val id: Long,
    val name: String,
    val description: String,
    val imageUrl: String
)