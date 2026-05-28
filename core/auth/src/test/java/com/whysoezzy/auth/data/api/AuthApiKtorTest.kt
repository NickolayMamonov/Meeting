package com.whysoezzy.auth.data.api

import com.whysoezzy.auth.data.dto.AuthResponse
import com.whysoezzy.auth.data.dto.AuthUserDto
import com.whysoezzy.testing.MainDispatcherRule
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import io.ktor.http.Headers

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

    private fun buildClient(mockEngine: MockEngine): HttpClient =
        HttpClient(mockEngine) {
            defaultRequest { url("http://test.local/") }
            install(ContentNegotiation) { json(json) }
        }

    // ==================== sendOtp ====================

    @Test
    fun `sendOtp sends POST to auth-send-otp with phone in body`() = runTest {
        var capturedBody = ""
        val engine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(content = """{"message":"ok"}""", headers = jsonHeaders)
        }

        AuthApiKtor(buildClient(engine)).sendOtp("+79991234567")

        assertTrue(capturedBody.contains("79991234567"))
    }

    @Test
    fun `sendOtp returns map on success`() = runTest {
        val engine = MockEngine {
            respond(content = """{"message":"OTP sent"}""", headers = jsonHeaders)
        }

        val result = AuthApiKtor(buildClient(engine)).sendOtp("+79991234567")

        assertEquals("OTP sent", result["message"])
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

        val result = AuthApiKtor(buildClient(engine)).verifyOtp("+79991234567", "1234")

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

        val result = AuthApiKtor(buildClient(engine)).verifyOtp("+79991234567", "5678")

        assertTrue(result.isNewUser)
    }

    @Test
    fun `verifyOtp sends correct phone and code in body`() = runTest {
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

        AuthApiKtor(buildClient(engine)).verifyOtp("+79991234567", "1234")

        assertTrue(capturedBody.contains("79991234567"))
        assertTrue(capturedBody.contains("1234"))
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

        val result = AuthApiKtor(buildClient(engine)).refreshToken("oldRefresh")

        assertEquals("newAccess", result.accessToken)
        assertEquals("newRefresh", result.refreshToken)
    }

    @Test
    fun `refreshToken with null refreshToken in response deserializes correctly`() = runTest {
        val engine = MockEngine {
            respond(content = """{"accessToken":"newAccess"}""", headers = jsonHeaders)
        }

        val result = AuthApiKtor(buildClient(engine)).refreshToken("oldRefresh")

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

        AuthApiKtor(buildClient(engine)).refreshToken("myOldRefreshToken")

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

        AuthApiKtor(buildClient(engine)).logout()

        assertTrue(capturedPath.contains("auth/logout"))
    }

    @Test
    fun `logout returns map on success`() = runTest {
        val engine = MockEngine {
            respond(content = """{"message":"logged out"}""", headers = jsonHeaders)
        }

        val result = AuthApiKtor(buildClient(engine)).logout()

        assertEquals("logged out", result["message"])
    }
}