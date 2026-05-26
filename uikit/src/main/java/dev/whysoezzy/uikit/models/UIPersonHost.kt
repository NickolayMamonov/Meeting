package dev.whysoezzy.uikit.models

import androidx.compose.runtime.Immutable

@Immutable
data class UIKitPersonHost(
    val id: Long,
    val name: String,
    val surname: String,
    val description: String,
    val imageUrl: String
)