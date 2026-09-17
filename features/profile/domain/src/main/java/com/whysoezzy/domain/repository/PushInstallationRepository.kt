package com.whysoezzy.domain.repository

import com.whysoezzy.domain.models.PushInstallationDeleteResult
import com.whysoezzy.domain.models.PushInstallationFid
import com.whysoezzy.domain.models.PushInstallationId
import com.whysoezzy.domain.models.PushInstallationUpsertResult

interface PushInstallationRepository {
    suspend fun create(
        fid: PushInstallationFid,
    ): Result<PushInstallationUpsertResult>

    suspend fun update(
        installationId: PushInstallationId,
        fid: PushInstallationFid,
    ): Result<PushInstallationUpsertResult>

    suspend fun delete(
        installationId: PushInstallationId,
    ): Result<PushInstallationDeleteResult>
}
