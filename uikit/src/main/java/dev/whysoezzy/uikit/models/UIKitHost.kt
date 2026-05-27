package dev.whysoezzy.uikit.models

import androidx.compose.runtime.Immutable

@Immutable
data class UIKitHost(
    val id: Long,
    val name: String,
    val surname: String,
    val description: String,
    val imageUrl: String,
)
