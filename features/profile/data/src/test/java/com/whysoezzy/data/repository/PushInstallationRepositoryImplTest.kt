package com.whysoezzy.data.repository

import com.whysoezzy.data.api.PushInstallationApi
import com.whysoezzy.data.api.PushInstallationDeleteApiResult
import com.whysoezzy.data.api.PushInstallationUpsertApiResult
import com.whysoezzy.data.dto.PushInstallationResponseDto
import com.whysoezzy.domain.models.PushInstallationDeleteResult
import com.whysoezzy.domain.models.PushInstallationFid
import com.whysoezzy.domain.models.PushInstallationId
import com.whysoezzy.domain.models.PushInstallationStatus
import com.whysoezzy.domain.models.PushInstallationTerminalStatus
import com.whysoezzy.domain.models.PushInstallationUpsertResult
import com.whysoezzy.network.error.ApiException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PushInstallationRepositoryImplTest {
    @Test
    fun `valid API response acknowledges pending upsert with typed values`() = runTest {
        val api = FakeApi(
            upsertResult = PushInstallationUpsertApiResult.Acknowledged(responseDto),
        )
        val repository = PushInstallationRepositoryImpl(api)

        val result = repository.create(fid).getOrThrow()

        assertTrue(result is PushInstallationUpsertResult.Acknowledged)
        val installation = (result as PushInstallationUpsertResult.Acknowledged).installation
        assertEquals(installationId, installation.installationId)
        assertEquals(PushInstallationStatus.ACTIVE, installation.status)
        assertEquals(Instant.parse(LAST_SEEN_AT), installation.lastSeenAt)
    }

    @Test
    fun `malformed success is terminal and does not acknowledge pending upsert`() = runTest {
        val repository = PushInstallationRepositoryImpl(
            FakeApi(upsertResult = PushInstallationUpsertApiResult.MalformedSuccess),
        )

        val result = repository.update(installationId, fid).getOrThrow()

        assertEquals(
            PushInstallationUpsertResult.Terminal(
                PushInstallationTerminalStatus.MALFORMED_SUCCESS,
            ),
            result,
        )
        assertTrue(result !is PushInstallationUpsertResult.Acknowledged)
    }

    @Test
    fun `delete acknowledgement and malformed success remain distinct`() = runTest {
        val acknowledged = PushInstallationRepositoryImpl(
            FakeApi(deleteResult = PushInstallationDeleteApiResult.Acknowledged),
        ).delete(installationId).getOrThrow()
        val malformed = PushInstallationRepositoryImpl(
            FakeApi(deleteResult = PushInstallationDeleteApiResult.MalformedSuccess),
        ).delete(installationId).getOrThrow()

        assertEquals(PushInstallationDeleteResult.Acknowledged, acknowledged)
        assertEquals(
            PushInstallationDeleteResult.Terminal(
                PushInstallationTerminalStatus.MALFORMED_SUCCESS,
            ),
            malformed,
        )
    }

    @Test
    fun `network failure remains a failed repository result`() = runTest {
        val repository = PushInstallationRepositoryImpl(
            FakeApi(failure = IOException("offline")),
        )

        val result = repository.create(fid)

        assertTrue(result.exceptionOrNull() is ApiException.NetworkError)
    }

    private class FakeApi(
        private val upsertResult: PushInstallationUpsertApiResult =
            PushInstallationUpsertApiResult.Acknowledged(responseDto),
        private val deleteResult: PushInstallationDeleteApiResult =
            PushInstallationDeleteApiResult.Acknowledged,
        private val failure: Throwable? = null,
    ) : PushInstallationApi {
        override suspend fun create(fid: String): PushInstallationUpsertApiResult {
            failure?.let { throw it }
            return upsertResult
        }

        override suspend fun update(
            installationId: String,
            fid: String,
        ): PushInstallationUpsertApiResult {
            failure?.let { throw it }
            return upsertResult
        }

        override suspend fun delete(installationId: String): PushInstallationDeleteApiResult {
            failure?.let { throw it }
            return deleteResult
        }
    }

    private companion object {
        const val INSTALLATION_ID = "550e8400-e29b-41d4-a716-446655440000"
        const val LAST_SEEN_AT = "2026-08-15T12:34:56Z"
        val installationId = PushInstallationId(INSTALLATION_ID)
        val fid = PushInstallationFid("opaque-firebase-installation-id")
        val responseDto = PushInstallationResponseDto(
            installationId = INSTALLATION_ID,
            status = "ACTIVE",
            lastSeenAt = LAST_SEEN_AT,
        )
    }
}
