package dev.whysoezzy.uikit.models

import androidx.compose.runtime.Immutable

@Immutable
data class UIKitPerson(
    val id: Long,
    val name: String,
    val surname: String,
    val avatar: String,
    val description: String = ""
)