package com.whysoezzy.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class PushInstallationRequestDto(
    @SerialName("fid") val fid: String,
)

@Serializable
internal data class PushInstallationResponseDto(
    @SerialName("installationId") val installationId: String,
    @SerialName("status") val status: String,
    @SerialName("lastSeenAt") val lastSeenAt: String,
)
