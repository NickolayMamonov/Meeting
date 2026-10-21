package dev.whysoezzy.uikit.models

sealed interface UIKitCommunitySubscriptionAction {
    data object ReadOnly : UIKitCommunitySubscriptionAction

    data class Actionable(
        val isSubscribed: Boolean,
        val onSubscribeClick: (Boolean) -> Unit,
    ) : UIKitCommunitySubscriptionAction
}
