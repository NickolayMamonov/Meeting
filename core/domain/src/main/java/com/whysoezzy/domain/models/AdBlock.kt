package com.whysoezzy.domain.models

sealed interface AdBlock {
    val id: Long
    val isActive: Boolean
    val title: String
    val description: String

    data class CommunitiesAd(
        override val id: Long,
        override val title: String,
        override val description: String,
        val communities: List<CommunityInfo>,
        override val isActive: Boolean = true,
    ) : AdBlock

    data class TextAd(
        override val id: Long,
        override val title: String,
        override val description: String,
        val actionText: String? = null,
        val actionUrl: String? = null,
        override val isActive: Boolean = true,
    ) : AdBlock

    data class PeopleAd(
        override val id: Long,
        override val title: String,
        override val description: String,
        val users: List<Person>,
        override val isActive: Boolean = true,
    ) : AdBlock
}
