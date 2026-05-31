package dev.whysoezzy.uikit.models

import androidx.compose.runtime.Immutable

@Immutable
sealed interface UIKitAdBlock {
    val id: Long
    val title: String
    val description: String

    @Immutable
    data class CommunitiesAd(
        override val id: Long,
        override val title: String,
        override val description: String,
        val communities: List<UIKitCommunityInfo>,
    ) : UIKitAdBlock

    @Immutable
    data class TextAd(
        override val id: Long,
        override val title: String,
        override val description: String,
        val actionText: String? = null,
        val actionUrl: String? = null,
    ) : UIKitAdBlock

    @Immutable
    data class PeopleAd(
        override val id: Long,
        override val title: String,
        override val description: String,
        val users: List<Person>,
    ) : UIKitAdBlock {
        @Immutable
        data class Person(
            val id: Long,
            val name: String,
            val avatarUrl: String,
            val role: String,
        )
    }
}