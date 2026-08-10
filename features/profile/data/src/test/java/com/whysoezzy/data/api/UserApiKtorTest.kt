package com.whysoezzy.data.api

import com.whysoezzy.data.dto.SocialMediaDto
import com.whysoezzy.data.dto.TagDto
import com.whysoezzy.data.dto.UpdateUserDto
import com.whysoezzy.data.dto.UserProfileDto
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserApiKtorTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    @Test
    fun `current profile request decodes nested profile fields`() = runTest {
        var method: HttpMethod? = null
        var path: String? = null
        val engine = MockEngine { request ->
            method = request.method
            path = request.url.encodedPath
            respond(
                content = json.encodeToString(sampleUserDto),
                headers = jsonHeaders,
            )
        }

        withClient(engine) { client ->
            val result = UserApiKtor(client).getCurrentUserProfile()

            assertEquals(42L, result.id)
            assertEquals("Moscow", result.city)
            assertEquals("Kotlin", result.interests.single().name)
            assertEquals("https://t.me/ada", result.socialMedias.single().url)
        }

        assertEquals(HttpMethod.Get, method)
        assertEquals("/profile", path)
    }

    @Test
    fun `update profile sends semantic JSON body`() = runTest {
        var body = ""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/profile", request.url.encodedPath)
            body = request.body.toByteArray().decodeToString()
            respond(content = json.encodeToString(sampleUserDto), headers = jsonHeaders)
        }
        val expected = UpdateUserDto(
            name = "Ada",
            city = "London",
            interestIds = listOf(1L, 2L),
            socialMedias = listOf(SocialMediaDto("telegram", "https://t.me/ada")),
            showMeetings = false,
        )

        withClient(engine) { UserApiKtor(it).updateUserProfile(expected) }

        assertEquals(expected, json.decodeFromString<UpdateUserDto>(body))
    }

    @Test
    fun `linked communities collection uses user path and decodes nested item`() = runTest {
        var path = ""
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            respond(
                content =
                    """
                    [
                        {"id":9,"name":"Android","description":"Mobile","imageUrl":"https://img/community.webp",
                         "subscribersCount":12,"isSubscribed":true}
                    ]
                    """.trimIndent(),
                headers = jsonHeaders,
            )
        }

        val result = withClient(engine) { UserApiKtor(it).getUserCommunities(42L) }

        assertEquals("/users/42/communities", path)
        assertEquals(9L, result.single().id)
        assertEquals(true, result.single().isSubscribed)
    }

    @Test
    fun `delete current user profile sends DELETE to profile and accepts empty no-content response`() = runTest {
        var method: HttpMethod? = null
        var path: String? = null
        val engine = MockEngine { request ->
            method = request.method
            path = request.url.encodedPath
            respond(content = "", status = HttpStatusCode.NoContent)
        }
        withClient(engine) { UserApiKtor(it).deleteCurrentUserProfile() }

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
        var requestBody = ByteArray(0)
        val capturingEngine = MockEngine { request ->
            method = request.method
            path = request.url.encodedPath
            requestBody = request.body.toByteArray()
            respond(
                content = """{"url":"https://cdn.example/avatar.webp"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        val result = withClient(capturingEngine) {
            UserApiKtor(it).uploadAvatar(sampleUpload) { _, _ -> }
        }

        assertEquals(HttpMethod.Post, method)
        assertEquals("/media/avatar", path)
        assertEquals("https://cdn.example/avatar.webp", result.url)
        assertTrue(
            requestBody.decodeToString().contains("filename=\"avatar.webp\""),
        )
        val multipartText = requestBody.decodeToString()
        assertTrue(multipartText.contains("name=file") || multipartText.contains("name=\"file\""))
        assertTrue(multipartText.contains("Content-Type: image/webp"))
        assertTrue(bytes.all { byte -> requestBody.contains(byte) })
    }

    private suspend fun assertDeleteFailure(status: HttpStatusCode) {
        val engine = MockEngine { respond(content = "", status = status) }
        withClient(engine) {
            try {
                UserApiKtor(it).deleteCurrentUserProfile()
                throw AssertionError("Expected HTTP failure")
            } catch (_: Exception) {
                assertTrue(true)
            }
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

        val sampleUserDto = UserProfileDto(
            id = 42L,
            name = "Ada",
            surname = "Lovelace",
            email = "ada@example.com",
            city = "Moscow",
            description = "Engineer",
            avatarUrl = "https://img.example/ada.webp",
            interests = listOf(TagDto(id = 1L, name = "Kotlin")),
            socialMedias = listOf(SocialMediaDto(type = "telegram", url = "https://t.me/ada")),
        )

        val sampleUpload = com.whysoezzy.domain.models.AvatarUpload(
            fileName = "avatar.webp",
            contentType = "image/webp",
            contentLength = 3,
            openStream = { java.io.ByteArrayInputStream(bytes) },
        )
        val bytes = byteArrayOf(1, 2, 3)
    }
}
