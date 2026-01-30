package dev.whysoezzy.uikit.models

data class UIKitCommunityInfo(
    val id: Long,
    val title: String,
    val imageUrl: String,
    val isSubscribed: Boolean,
    val onSubscribeClick: (Boolean) -> Unit,
    val onCardClick: (() -> Unit)? = null
)
