package dev.whysoezzy.uikit.models

import androidx.compose.runtime.Immutable

@Immutable
data class UIKitAddress(
    val address: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
)
