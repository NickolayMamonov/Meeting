package dev.whysoezzy.data.remote.dto

import com.google.gson.annotations.SerializedName

sealed class AdBlockDto {
    abstract val id: String
    abstract val type: String
    abstract val priority: Int

    data class CommunityAdDto(
        @SerializedName("id") override val id: String,
        @SerializedName("type") override val type: String,
        @SerializedName("priority") override val priority: Int,
        @SerializedName("title") val title: String,
        @SerializedName("communities") val communities: List<CommunityDto>
    ) : AdBlockDto()

    data class BannerAdDto(
        @SerializedName("id") override val id: String,
        @SerializedName("type") override val type: String,
        @SerializedName("priority") override val priority: Int,
        @SerializedName("title") val title: String,
        @SerializedName("image_url") val imageUrl: String,
        @SerializedName("action_text") val actionText: String,
        @SerializedName("deep_link") val deepLink: String
    ) : AdBlockDto()

    data class TextAdDto(
        @SerializedName("id") override val id: String,
        @SerializedName("type") override val type: String,
        @SerializedName("priority") override val priority: Int,
        @SerializedName("title") val title: String,
        @SerializedName("description") val description: String,
        @SerializedName("action_text") val actionText: String?,
        @SerializedName("deep_link") val deepLink: String?
    ) : AdBlockDto()
}