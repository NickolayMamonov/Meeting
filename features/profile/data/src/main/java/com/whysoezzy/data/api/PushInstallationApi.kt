package com.whysoezzy.data.api

import com.whysoezzy.data.dto.PushInstallationResponseDto

internal interface PushInstallationApi {
    suspend fun create(fid: String): PushInstallationUpsertApiResult

    suspend fun update(
        installationId: String,
        fid: String,
    ): PushInstallationUpsertApiResult

    suspend fun delete(installationId: String): PushInstallationDeleteApiResult
}

internal sealed interface PushInstallationUpsertApiResult {
    data class Acknowledged(
        val installation: PushInstallationResponseDto,
    ) : PushInstallationUpsertApiResult

    data object MalformedSuccess : PushInstallationUpsertApiResult
}

internal sealed interface PushInstallationDeleteApiResult {
    data object Acknowledged : PushInstallationDeleteApiResult

    data object MalformedSuccess : PushInstallationDeleteApiResult
}
