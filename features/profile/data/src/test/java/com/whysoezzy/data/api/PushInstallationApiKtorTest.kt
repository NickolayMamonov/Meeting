package com.whysoezzy.data.api

import com.whysoezzy.data.dto.PushInstallationRequestDto
import com.whysoezzy.network.KtorNetworkModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PushInstallationApiKtorTest {
    @Test
    fun `POST sends only fid and accepts 200 or 201`() = runTest {
        for (status in listOf(HttpStatusCode.OK, HttpStatusCode.Created)) {
            var method: HttpMethod? = null
            var path = ""
            var body = ""
            val engine = MockEngine { request ->
                method = request.method
                path = request.url.encodedPath
                body = request.body.toByteArray().decodeToString()
                respond(validResponse, status, jsonHeaders)
            }

            val result = withApi(engine) { it.create(FID) }

            assertTrue(result is PushInstallationUpsertApiResult.Acknowledged)
            assertEquals(HttpMethod.Post, method)
            assertEquals("/profile/push-installations", path)
            assertEquals(
                PushInstallationRequestDto(FID),
                json.decodeFromString<PushInstallationRequestDto>(body),
            )
        }
    }

    @Test
    fun `PUT sends fid to installation path and requires matching response id`() = runTest {
        var method: HttpMethod? = null
        var path = ""
        var body = ""
        val engine = MockEngine { request ->
            method = request.method
            path = request.url.encodedPath
            body = request.body.toByteArray().decodeToString()
            respond(validResponse, HttpStatusCode.OK, jsonHeaders)
        }

        val result = withApi(engine) { it.update(INSTALLATION_ID, FID) }

        assertTrue(result is PushInstallationUpsertApiResult.Acknowledged)
        assertEquals(HttpMethod.Put, method)
        assertEquals("/profile/push-installations/$INSTALLATION_ID", path)
        assertEquals(
            PushInstallationRequestDto(FID),
            json.decodeFromString<PushInstallationRequestDto>(body),
        )
    }

    @Test
    fun `DELETE accepts 204 and idempotent 404`() = runTest {
        for (status in listOf(HttpStatusCode.NoContent, HttpStatusCode.NotFound)) {
            var method: HttpMethod? = null
            var path = ""
            val engine = MockEngine { request ->
                method = request.method
                path = request.url.encodedPath
                respond("", status)
            }

            val result = withApi(engine) { it.delete(INSTALLATION_ID) }

            assertEquals(PushInstallationDeleteApiResult.Acknowledged, result)
            assertEquals(HttpMethod.Delete, method)
            assertEquals("/profile/push-installations/$INSTALLATION_ID", path)
        }
    }

    @Test
    fun `valid response tolerates additive unknown fields`() = runTest {
        val response =
            """
            {
              "installationId":"$INSTALLATION_ID",
              "status":"ACTIVE",
              "lastSeenAt":"2026-08-15T12:34:56.123Z",
              "serverMetadata":{"generation":2},
              "capabilities":["reminders"]
            }
            """.trimIndent()
        val engine = MockEngine {
            respond(response, HttpStatusCode.Created, jsonHeaders)
        }

        val result = withApi(engine) { it.create(FID) }

        assertTrue(result is PushInstallationUpsertApiResult.Acknowledged)
    }

    @Test
    fun `unsupported successful statuses are malformed success`() = runTest {
        val cases = listOf(
            HttpMethod.Post to HttpStatusCode.Accepted,
            HttpMethod.Post to HttpStatusCode.NoContent,
            HttpMethod.Put to HttpStatusCode.Created,
            HttpMethod.Put to HttpStatusCode.NoContent,
        )

        for ((method, status) in cases) {
            val engine = MockEngine {
                respond(validResponse, status, jsonHeaders)
            }
            val result = withApi(engine) {
                if (method == HttpMethod.Post) it.create(FID) else it.update(INSTALLATION_ID, FID)
            }

            assertEquals(PushInstallationUpsertApiResult.MalformedSuccess, result)
        }

        val deleteEngine = MockEngine {
            respond("", HttpStatusCode.OK)
        }
        val deleteResult = withApi(deleteEngine) { it.delete(INSTALLATION_ID) }
        assertEquals(PushInstallationDeleteApiResult.MalformedSuccess, deleteResult)
    }

    @Test
    fun `malformed POST success bodies are rejected without acknowledgement`() = runTest {
        val malformedBodies = listOf(
            "",
            "null",
            "[]",
            """{"status":"ACTIVE","lastSeenAt":"2026-08-15T12:34:56Z"}""",
            """
            {"installationId":"$INSTALLATION_ID",
             "lastSeenAt":"2026-08-15T12:34:56Z"}
            """.trimIndent(),
            """{"installationId":"$INSTALLATION_ID","status":"ACTIVE"}""",
            """
            {"installationId":null,"status":"ACTIVE",
             "lastSeenAt":"2026-08-15T12:34:56Z"}
            """.trimIndent(),
            """
            {"installationId":"$INSTALLATION_ID","status":null,
             "lastSeenAt":"2026-08-15T12:34:56Z"}
            """.trimIndent(),
            """
            {"installationId":"$INSTALLATION_ID","status":"ACTIVE",
             "lastSeenAt":null}
            """.trimIndent(),
            """
            {"installationId":"$INSTALLATION_ID","installationId":"$INSTALLATION_ID",
             "status":"ACTIVE","lastSeenAt":"2026-08-15T12:34:56Z"}
            """.trimIndent(),
            """
            {"installationId":"$INSTALLATION_ID","status":"ACTIVE","status":"ACTIVE",
             "lastSeenAt":"2026-08-15T12:34:56Z"}
            """.trimIndent(),
            """
            {"installationId":"$INSTALLATION_ID","status":"ACTIVE",
             "lastSeenAt":"2026-08-15T12:34:56Z",
             "lastSeenAt":"2026-08-15T12:34:56Z"}
            """.trimIndent(),
            """
            {"installationId":"$INSTALLATION_ID","status":"ACTIVE",
             "lastSeenAt":"2026-08-15T12:34:56Z","fid":"returned-secret"}
            """.trimIndent(),
            """
            {"installationId":"550E8400-E29B-41D4-A716-446655440000","status":"ACTIVE",
             "lastSeenAt":"2026-08-15T12:34:56Z"}
            """.trimIndent(),
            """
            {"installationId":"{550e8400-e29b-41d4-a716-446655440000}","status":"ACTIVE",
             "lastSeenAt":"2026-08-15T12:34:56Z"}
            """.trimIndent(),
            """
            {"installationId":"550e8400-e29b-41d4-a716-44665544000","status":"ACTIVE",
             "lastSeenAt":"2026-08-15T12:34:56Z"}
            """.trimIndent(),
            """
            {"installationId":"$INSTALLATION_ID","status":"INACTIVE",
             "lastSeenAt":"2026-08-15T12:34:56Z"}
            """.trimIndent(),
            """
            {"installationId":"$INSTALLATION_ID","status":"ACTIVE",
             "lastSeenAt":"2026-08-15T12:34:56"}
            """.trimIndent(),
            """
            {"installationId":"$INSTALLATION_ID","status":"ACTIVE",
             "lastSeenAt":"2026-08-15T12:34:56+00:00"}
            """.trimIndent(),
            """
            {"installationId":"$INSTALLATION_ID","status":"ACTIVE",
             "lastSeenAt":"2026-08-15T12:34:56z"}
            """.trimIndent(),
            """
            {"installationId":"$INSTALLATION_ID","status":"ACTIVE",
             "lastSeenAt":"2026-08-15T12:34:56.1234Z"}
            """.trimIndent(),
            """
            {"installationId":"$INSTALLATION_ID","status":"ACTIVE",
             "lastSeenAt":"not-an-instant"}
            """.trimIndent(),
        )

        for (body in malformedBodies) {
            val engine = MockEngine {
                respond(body, HttpStatusCode.OK, jsonHeaders)
            }

            val result = withApi(engine) { it.create(FID) }

            assertEquals(
                "Body should be malformed: $body",
                PushInstallationUpsertApiResult.MalformedSuccess,
                result,
            )
        }
    }

    @Test
    fun `PUT response id mismatch is malformed success`() = runTest {
        val response =
            """
            {"installationId":"00000000-0000-0000-0000-000000000001","status":"ACTIVE",
             "lastSeenAt":"2026-08-15T12:34:56Z"}
            """.trimIndent()
        val engine = MockEngine {
            respond(response, HttpStatusCode.OK, jsonHeaders)
        }

        val result = withApi(engine) { it.update(INSTALLATION_ID, FID) }

        assertEquals(PushInstallationUpsertApiResult.MalformedSuccess, result)
    }

    @Test
    fun `non-success HTTP status remains a transport failure`() = runTest {
        val engine = MockEngine {
            respond("", HttpStatusCode.Forbidden)
        }

        val result = runCatching {
            withApi(engine) { it.create(FID) }
        }

        assertTrue(result.isFailure)
    }

    private suspend fun <T> withApi(
        engine: MockEngine,
        block: suspend (PushInstallationApiKtor) -> T,
    ): T {
        val client: HttpClient = KtorNetworkModule.provideHttpClient(engine)
        return try {
            block(PushInstallationApiKtor(client))
        } finally {
            client.close()
        }
    }

    private companion object {
        const val INSTALLATION_ID = "550e8400-e29b-41d4-a716-446655440000"
        const val FID = "opaque-firebase-installation-id"
        val json = Json
        val jsonHeaders = Headers.build {
            append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        }
        val validResponse =
            """
            {"installationId":"$INSTALLATION_ID","status":"ACTIVE",
             "lastSeenAt":"2026-08-15T12:34:56Z"}
            """.trimIndent()
    }
}
