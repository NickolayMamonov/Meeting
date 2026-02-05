package com.whysoezzy.domain.models

sealed class AdBlock {
    abstract val id: Long
    abstract val isActive: Boolean
    abstract val title: String
    abstract val description: String

    data class CommunitiesAd(
        override val id: Long,
        override val title: String,
        override val description: String,
        val communities: List<CommunityInfo>,
        override val isActive: Boolean = true
    ) : AdBlock()

    data class TextAd(
        override val id: Long,
        override val title: String,
        override val description: String,
        val actionText: String? = null,
        val actionUrl: String? = null,
        override val isActive: Boolean = true
    ) : AdBlock()

    data class PeopleAd(
        override val id: Long,
        override val title: String,
        override val description: String,
        val users: List<Person>,
        override val isActive: Boolean = true
    ) : AdBlock()
}