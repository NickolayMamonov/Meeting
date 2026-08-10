package com.whysoezzy.auth.data.api

import com.whysoezzy.auth.data.dto.AuthResponse
import com.whysoezzy.auth.data.dto.AuthUserDto
import com.whysoezzy.auth.data.dto.SendOtpRequest
import com.whysoezzy.auth.data.dto.VerifyOtpRequest
import com.whysoezzy.network.KtorNetworkModule
import com.whysoezzy.testing.MainDispatcherRule
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthApiKtorTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }

    private val jsonHeaders = Headers.build {
        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }

    private suspend fun <T> withClient(
        mockEngine: MockEngine,
        block: suspend (HttpClient) -> T,
    ): T {
        val client = KtorNetworkModule.provideHttpClient(mockEngine)
        return try {
            block(client)
        } finally {
            client.close()
        }
    }

    @Test
    fun `auth operations use exact HTTP contracts`() = runTest {
        val requests = mutableListOf<io.ktor.client.request.HttpRequestData>()
        val response = AuthResponse(
            accessToken = "access",
            refreshToken = "refresh",
            isNewUser = false,
            user = AuthUserDto(id = 7L, name = "Ada", surname = "Lovelace"),
        )
        val engine = MockEngine { request ->
            requests += request
            when (request.url.encodedPath) {
                "/auth/email/send-otp" -> respond(
                    content = """{"message":"sent"}""",
                    headers = jsonHeaders,
                )
                "/auth/email/verify-otp" -> respond(
                    content = json.encodeToString(response),
                    headers = jsonHeaders,
                )
                "/auth/refresh" -> respond(
                    content = """{"accessToken":"new-access","refreshToken":"new-refresh"}""",
                    headers = jsonHeaders,
                )
                "/auth/logout" -> respond(
                    content = """{"message":"logged out"}""",
                    headers = jsonHeaders,
                )
                else -> error("Unexpected auth path: ${request.url.encodedPath}")
            }
        }

        withClient(engine) { client ->
            val api = AuthApiKtor(client)
            api.requestEmailOtp("person@example.com")
            api.verifyEmailOtp("person@example.com", "123456")
            api.refreshToken("old-refresh")
            api.logout()
        }

        assertEquals(
            listOf(
                HttpMethod.Post to "/auth/email/send-otp",
                HttpMethod.Post to "/auth/email/verify-otp",
                HttpMethod.Post to "/auth/refresh",
                HttpMethod.Post to "/auth/logout",
            ),
            requests.map { it.method to it.url.encodedPath },
        )
        assertTrue(requests[0].body.toByteArray().isNotEmpty())
        assertTrue(requests[1].body.toByteArray().isNotEmpty())
        assertTrue(requests[2].body.toByteArray().isNotEmpty())
    }

    // ==================== email OTP request ====================

    @Test
    fun `sendOtp sends canonical email in body`() = runTest {
        var capturedBody = ""
        val engine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(content = """{"message":"ok"}""", headers = jsonHeaders)
        }

        withClient(engine) { AuthApiKtor(it).requestEmailOtp("person@example.com") }

        assertEquals(
            SendOtpRequest("person@example.com"),
            json.decodeFromString<SendOtpRequest>(capturedBody),
        )
    }

    @Test
    fun `sendOtp returns acknowledgement on success`() = runTest {
        val engine = MockEngine {
            respond(content = """{"message":"OTP sent"}""", headers = jsonHeaders)
        }

        val result = withClient(engine) { AuthApiKtor(it).requestEmailOtp("person@example.com") }

        assertEquals("OTP sent", result.message)
    }

    // ==================== verifyOtp ====================

    @Test
    fun `verifyOtp deserializes AuthResponse correctly`() = runTest {
        val expected = AuthResponse(
            accessToken = "access123",
            refreshToken = "refresh456",
            isNewUser = false,
            user = AuthUserDto(id = 1L, name = "Иван", surname = "Иванов"),
        )
        val engine = MockEngine {
            respond(content = json.encodeToString(expected), headers = jsonHeaders)
        }

        val result = withClient(engine) { AuthApiKtor(it).verifyEmailOtp("person@example.com", "123456") }

        assertEquals("access123", result.accessToken)
        assertEquals("refresh456", result.refreshToken)
        assertEquals(false, result.isNewUser)
        assertEquals(1L, result.user.id)
    }

    @Test
    fun `verifyOtp isNewUser=true deserializes correctly`() = runTest {
        val response = AuthResponse(
            accessToken = "token",
            refreshToken = "refresh",
            isNewUser = true,
            user = AuthUserDto(id = 2L, name = "Новый", surname = "Пользователь"),
        )
        val engine = MockEngine {
            respond(content = json.encodeToString(response), headers = jsonHeaders)
        }

        val result = withClient(engine) { AuthApiKtor(it).verifyEmailOtp("person@example.com", "567890") }

        assertTrue(result.isNewUser)
    }

    @Test
    fun `verifyOtp sends correct email and six digit code in body`() = runTest {
        var capturedBody = ""
        val response = AuthResponse(
            accessToken = "token",
            refreshToken = "refresh",
            isNewUser = false,
            user = AuthUserDto(id = 1L, name = "Иван", surname = "Иванов"),
        )
        val engine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(content = json.encodeToString(response), headers = jsonHeaders)
        }

        withClient(engine) { AuthApiKtor(it).verifyEmailOtp("person@example.com", "123456") }

        assertEquals(
            VerifyOtpRequest(
                email = "person@example.com",
                code = "123456",
                name = null,
                surname = null,
            ),
            json.decodeFromString<VerifyOtpRequest>(capturedBody),
        )
    }

    // ==================== refreshToken ====================

    @Test
    fun `refreshToken deserializes new tokens correctly`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"accessToken":"newAccess","refreshToken":"newRefresh"}""",
                headers = jsonHeaders,
            )
        }

        val result = withClient(engine) { AuthApiKtor(it).refreshToken("oldRefresh") }

        assertEquals("newAccess", result.accessToken)
        assertEquals("newRefresh", result.refreshToken)
    }

    @Test
    fun `refreshToken with null refreshToken in response deserializes correctly`() = runTest {
        val engine = MockEngine {
            respond(content = """{"accessToken":"newAccess"}""", headers = jsonHeaders)
        }

        val result = withClient(engine) { AuthApiKtor(it).refreshToken("oldRefresh") }

        assertEquals("newAccess", result.accessToken)
        assertEquals(null, result.refreshToken)
    }

    @Test
    fun `refreshToken sends refresh token in request body`() = runTest {
        var capturedBody = ""
        val engine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"accessToken":"newAccess","refreshToken":"newRefresh"}""",
                headers = jsonHeaders,
            )
        }

        withClient(engine) { AuthApiKtor(it).refreshToken("myOldRefreshToken") }

        assertTrue(capturedBody.contains("myOldRefreshToken"))
    }

    // ==================== logout ====================

    @Test
    fun `logout sends POST to auth-logout path`() = runTest {
        var capturedPath = ""
        val engine = MockEngine { request ->
            capturedPath = request.url.encodedPath
            respond(content = """{"message":"logged out"}""", headers = jsonHeaders)
        }

        withClient(engine) { AuthApiKtor(it).logout() }

        assertTrue(capturedPath.contains("auth/logout"))
    }

    @Test
    fun `logout returns map on success`() = runTest {
        val engine = MockEngine {
            respond(content = """{"message":"logged out"}""", headers = jsonHeaders)
        }

        val result = withClient(engine) { AuthApiKtor(it).logout() }

        assertEquals("logged out", result["message"])
    }
}
