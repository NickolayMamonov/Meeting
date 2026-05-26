package dev.whysoezzy.uikit.models

import androidx.compose.runtime.Immutable

@Immutable
data class UIKitTag(
    val text: String,
    val isSelected: Boolean = false,
    val isEnabled: Boolean = true
)