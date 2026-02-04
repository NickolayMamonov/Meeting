package com.whysoezzy.domain.models

sealed class AdBlock {
    abstract val id: Long
    abstract val isActive: Boolean

    data class CommunityAd(
        override val id: Long,
        val communityId: Long,
        val communityName: String,
        val communityDescription: String,
        val communityImageUrl: String,
        val subscribersCount: Int = 0,
        override val isActive: Boolean = true
    ) : AdBlock()

    data class TextAd(
        override val id: Long,
        val title: String,
        val description: String,
        val actionText: String? = null,
        val actionUrl: String? = null,
        override val isActive: Boolean = true
    ) : AdBlock()

    data class BannerAd(
        override val id: Long,
        val title: String,
        val imageUrl: String,
        val actionUrl: String,
        val backgroundColor: String? = null,
        override val isActive: Boolean = true
    ) : AdBlock()
}