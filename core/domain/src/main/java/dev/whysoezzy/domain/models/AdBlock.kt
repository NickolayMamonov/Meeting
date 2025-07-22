package dev.whysoezzy.domain.models

sealed class AdBlock {
    abstract val id: Long
    abstract val priority: Int

    data class CommunityAd(
        override val id: Long,
        override val priority: Int,
        val title: String,
        val communities: List<CommunityInfo>,
    ) : AdBlock()

    data class BannerAd(
        override val id: Long,
        override val priority: Int,
        val title: String,
        val imageUrl: String,
        val actionText: String,
        val deepLink: String
    ) : AdBlock()

    data class TextAd(
        override val id: Long,
        override val priority: Int,
        val title: String,
        val imageUrl: String,
        val actionText: String,
        val deepLink: String

    ) : AdBlock()
}