package dev.whysoezzy.uikit.models

data class UIKitPerson(
    val id: Long,
    val name: String,
    val surname: String,
    val avatar: String,
    val description: String = ""
)