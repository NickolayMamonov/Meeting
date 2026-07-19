package com.whysoezzy.data.api

import com.whysoezzy.data.repository.UserRepositoryImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserApiKtorTest {
    @Test
    fun `delete current user profile sends DELETE to profile and accepts empty no-content response`() = runTest {
        var method: HttpMethod? = null
        var path: String? = null
        val engine = MockEngine { request ->
            method = request.method
            path = request.url.encodedPath
            respond(content = "", status = HttpStatusCode.NoContent)
        }
        val client = HttpClient(engine) {
            defaultRequest { url("http://test.local/") }
        }

        UserApiKtor(client).deleteCurrentUserProfile()

        assertEquals(HttpMethod.Delete, method)
        assertEquals("/profile", path)
    }

    @Test
    fun `delete current user profile returns failure for a client error response`() = runTest {
        assertDeleteFailure(HttpStatusCode.BadRequest)
    }

    @Test
    fun `delete current user profile returns failure for a server error response`() = runTest {
        assertDeleteFailure(HttpStatusCode.InternalServerError)
    }

    @Test
    fun `upload avatar sends multipart file to media avatar and returns URL`() = runTest {
        var method: HttpMethod? = null
        var path: String? = null
        val engine = MockEngine { request ->
            method = request.method
            path = request.url.encodedPath
            respond(
                content = """{"url":"https://cdn.example/avatar.webp"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            defaultRequest { url("http://test.local/") }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val result = UserApiKtor(client).uploadAvatar(sampleUpload) { _, _ -> }

        assertEquals(HttpMethod.Post, method)
        assertEquals("/media/avatar", path)
        assertEquals("https://cdn.example/avatar.webp", result.url)
    }

    private suspend fun assertDeleteFailure(status: HttpStatusCode) {
        val engine = MockEngine { respond(content = "", status = status) }
        val client = HttpClient(engine) {
            defaultRequest { url("http://test.local/") }
        }

        val result = UserRepositoryImpl(UserApiKtor(client)).deleteCurrentUserProfile()

        assertTrue(result.isFailure)
    }

    private companion object {
        val sampleUpload = com.whysoezzy.domain.models.AvatarUpload(
            fileName = "avatar.webp",
            contentType = "image/webp",
            contentLength = 1,
            openStream = { java.io.ByteArrayInputStream(byteArrayOf(1)) },
        )
    }
}
