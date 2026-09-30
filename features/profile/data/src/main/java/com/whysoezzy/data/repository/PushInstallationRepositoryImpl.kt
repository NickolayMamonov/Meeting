package com.whysoezzy.data.repository

import com.whysoezzy.data.api.PushInstallationApi
import com.whysoezzy.data.api.PushInstallationDeleteApiResult
import com.whysoezzy.data.api.PushInstallationUpsertApiResult
import com.whysoezzy.data.dto.PushInstallationResponseDto
import com.whysoezzy.domain.models.PushInstallation
import com.whysoezzy.domain.models.PushInstallationDeleteResult
import com.whysoezzy.domain.models.PushInstallationFid
import com.whysoezzy.domain.models.PushInstallationId
import com.whysoezzy.domain.models.PushInstallationStatus
import com.whysoezzy.domain.models.PushInstallationTerminalStatus
import com.whysoezzy.domain.models.PushInstallationUpsertResult
import com.whysoezzy.domain.repository.PushInstallationRepository
import com.whysoezzy.network.safeApiCall
import java.time.Instant

internal class PushInstallationRepositoryImpl(
    private val api: PushInstallationApi,
) : PushInstallationRepository {
    override suspend fun create(
        fid: PushInstallationFid,
    ): Result<PushInstallationUpsertResult> = safeApiCall {
        api.create(fid.value).toDomain()
    }

    override suspend fun update(
        installationId: PushInstallationId,
        fid: PushInstallationFid,
    ): Result<PushInstallationUpsertResult> = safeApiCall {
        api.update(installationId.value, fid.value).toDomain()
    }

    override suspend fun delete(
        installationId: PushInstallationId,
    ): Result<PushInstallationDeleteResult> = safeApiCall {
        when (api.delete(installationId.value)) {
            PushInstallationDeleteApiResult.Acknowledged ->
                PushInstallationDeleteResult.Acknowledged

            PushInstallationDeleteApiResult.MalformedSuccess ->
                PushInstallationDeleteResult.Terminal(
                    PushInstallationTerminalStatus.MALFORMED_SUCCESS,
                )
        }
    }
}

private fun PushInstallationUpsertApiResult.toDomain(): PushInstallationUpsertResult =
    when (this) {
        is PushInstallationUpsertApiResult.Acknowledged ->
            PushInstallationUpsertResult.Acknowledged(installation.toDomain())

        PushInstallationUpsertApiResult.MalformedSuccess ->
            PushInstallationUpsertResult.Terminal(
                PushInstallationTerminalStatus.MALFORMED_SUCCESS,
            )
    }

private fun PushInstallationResponseDto.toDomain(): PushInstallation =
    PushInstallation(
        installationId = PushInstallationId(installationId),
        status = PushInstallationStatus.valueOf(status),
        lastSeenAt = Instant.parse(lastSeenAt),
    )
