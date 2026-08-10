package com.whysoezzy.data.repository

import com.whysoezzy.data.api.UserApiKtor
import com.whysoezzy.domain.models.AvatarUpload
import com.whysoezzy.network.KtorNetworkModule
import com.whysoezzy.network.error.ApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserRepositoryContractTest {
    @Test
    fun `real profile API maps profile and linked nested fields to domain`() = runTest {
        val engine = MockEngine {
            respond(
                content =
                    """
                    {
                      "id": 42,
                      "name": "Ada",
                      "surname": "Lovelace",
                      "email": "ada@example.com",
                      "city": "Moscow",
                      "description": "Engineer",
                      "avatarUrl": "https://img.example/ada.webp",
                      "interests": [{"id":1,"text":"Kotlin"}],
                      "socialMedias": [{"type":"telegram","url":"https://t.me/ada"}]
                    }
                    """.trimIndent(),
                headers = jsonHeaders,
            )
        }

        withClient(engine) { client ->
            val result = UserRepositoryImpl(UserApiKtor(client)).getCurrentUser()

            assertTrue(result.isSuccess)
            val user = result.getOrThrow()
            assertEquals(42L, user.id)
            assertEquals("Engineer", user.bio)
            assertEquals("Kotlin", user.interests.single().name)
            assertEquals("ada", user.socialMedias.single().username)
        }
    }

    @Test
    fun `401 and 403 map to unauthorized while another backend status preserves metadata`() = runTest {
        for (status in listOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden)) {
            val engine = MockEngine {
                respond(
                    content =
                        """
                        {"status":${status.value},"code":"PROFILE_AUTH","message":"private",
                         "timestamp":"synthetic","path":"/profile"}
                        """.trimIndent(),
                    status = status,
                    headers = jsonHeaders,
                )
            }

            withClient(engine) { client ->
                val result = UserRepositoryImpl(UserApiKtor(client)).getCurrentUser()
                assertTrue(result.exceptionOrNull() is ApiException.UnauthorizedError)
                assertEquals(
                    status.value,
                    (result.exceptionOrNull() as ApiException.UnauthorizedError).metadata?.status,
                )
            }
        }

        val engine = MockEngine {
            respond(
                content =
                    """
                    {"status":409,"code":"PROFILE_CONFLICT","message":"private",
                     "timestamp":"synthetic","path":"/profile"}
                    """.trimIndent(),
                status = HttpStatusCode.Conflict,
                headers = jsonHeaders,
            )
        }
        withClient(engine) { client ->
            val result = UserRepositoryImpl(UserApiKtor(client)).getCurrentUser()
            val error = result.exceptionOrNull()
            assertTrue(error is ApiException.ServerError)
            assertEquals(409, (error as ApiException.ServerError).metadata.status)
            assertEquals("PROFILE_CONFLICT", error.metadata.code)
        }
    }

    @Test
    fun `malformed profile payload fails instead of producing an incomplete domain object`() = runTest {
        val engine = MockEngine {
            respond(content = """{"id":42,"name":"Ada"}""", headers = jsonHeaders)
        }

        withClient(engine) { client ->
            val result = UserRepositoryImpl(UserApiKtor(client)).getCurrentUser()
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is ApiException.UnknownError)
        }
    }

    @Test
    fun `IOException during avatar upload maps to network error`() = runTest {
        val engine = MockEngine {
            throw IOException("synthetic transport detail")
        }
        val upload = AvatarUpload(
            fileName = "avatar.webp",
            contentType = "image/webp",
            contentLength = 0,
            openStream = { java.io.ByteArrayInputStream(ByteArray(0)) },
        )

        withClient(engine) { client ->
            val result = UserRepositoryImpl(UserApiKtor(client)).uploadAvatar(upload) { _, _ -> }
            assertTrue(result.exceptionOrNull() is ApiException.NetworkError)
        }
    }

    private suspend fun <T> withClient(
        engine: MockEngine,
        block: suspend (HttpClient) -> T,
    ): T {
        val client = KtorNetworkModule.provideHttpClient(engine)
        return try {
            block(client)
        } finally {
            client.close()
        }
    }

    private companion object {
        val jsonHeaders = Headers.build {
            append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        }
    }
}
