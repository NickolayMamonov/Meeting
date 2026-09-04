package com.whysoezzy.auth.data.repository

import com.whysoezzy.auth.TokenManager
import com.whysoezzy.auth.data.api.AuthApiKtor
import com.whysoezzy.auth.domain.models.AuthFailure
import com.whysoezzy.auth.domain.models.AuthOutcome
import com.whysoezzy.auth.domain.models.AuthCredentialIdentity
import com.whysoezzy.auth.domain.models.AuthCredentialRead
import com.whysoezzy.auth.domain.models.AuthCredentialSnapshot
import com.whysoezzy.auth.domain.models.AuthOperationPermit
import com.whysoezzy.auth.domain.models.AuthRefreshSaveResult
import com.whysoezzy.auth.domain.models.AuthSaveResult
import com.whysoezzy.auth.domain.models.AuthSession
import com.whysoezzy.auth.domain.models.CredentialVersion
import com.whysoezzy.auth.domain.models.OwnerSaveReservation
import com.whysoezzy.auth.domain.models.PersistedTokenPair
import com.whysoezzy.network.KtorNetworkModule
import com.whysoezzy.network.error.ApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryContractTest {
    private val tokenManager = mockk<TokenManager>(relaxed = true)
    private val refreshPermit = AuthOperationPermit(
        generation = 0L,
        identity = AuthCredentialIdentity(
            userId = 7L,
            stage = AuthSession.Stage.Ready,
            credentialVersion = CredentialVersion("epoch", 0L),
            refreshToken = "old-refresh",
        ),
    )
    private val refreshSnapshot = AuthCredentialSnapshot(
        accessToken = "old-access",
        refreshToken = "old-refresh",
        userId = 7L,
        stage = AuthSession.Stage.Ready,
        credentialVersion = CredentialVersion("epoch", 0L),
    )

    private fun stubRefresh() {
        coEvery { tokenManager.readCredentialSnapshot(refreshPermit) } returns
            AuthCredentialRead.Present(refreshSnapshot, refreshPermit)
        coEvery { tokenManager.saveRefreshedTokens(refreshPermit, any(), any()) } returns
            AuthRefreshSaveResult.Persisted(PersistedTokenPair("new-access", "new-refresh"))
    }

    @Test
    fun `real auth API maps nested verification response and persists session`() = runTest {
        every { tokenManager.isLoggedInFlow } returns MutableStateFlow(false)
        val ownerPermit = AuthOperationPermit(1L, null)
        val ownerReservation = OwnerSaveReservation(1L, 1L, ownerPermit)
        every { tokenManager.reserveOwnerSave() } returns ownerReservation
        coEvery {
            tokenManager.saveAuthenticated(ownerReservation, any(), any(), any(), any())
        } returns AuthSaveResult.Persisted
        val engine = MockEngine {
            respond(
                content =
                    """
                    {
                      "accessToken": "access-7",
                      "refreshToken": "refresh-7",
                      "isNewUser": false,
                      "user": {
                        "id": 7,
                        "name": "Ada",
                        "surname": "Lovelace",
                        "email": "ada@example.com",
                        "avatarUrl": "https://cdn.example/ada.webp"
                      }
                    }
                    """.trimIndent(),
                headers = jsonHeaders,
            )
        }
        withClient(engine) { client ->
            val result = repository(client).verifyEmailOtp("ada@example.com", "123456")

            assertTrue(result is AuthOutcome.Success)
            val authResult = (result as AuthOutcome.Success).value
            assertEquals(7L, authResult.userId)
            assertEquals("access-7", authResult.accessToken)
            assertEquals(false, authResult.isNewUser)
        }
    }

    @Test
    fun `401 and 403 are unauthorized while other status preserves server metadata`() = runTest {
        every { tokenManager.isLoggedInFlow } returns MutableStateFlow(false)
        stubRefresh()

        for (status in listOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden)) {
            val engine = MockEngine {
                respond(
                    content =
                        """
                        {"status":${status.value},"code":"AUTH_REQUIRED","message":"private",
                         "timestamp":"synthetic","path":"/auth/email/send-otp"}
                        """.trimIndent(),
                    status = status,
                    headers = jsonHeaders,
                )
            }

            withClient(engine) { client ->
                val result = repository(client).requestEmailOtp("ada@example.com")
                assertEquals(AuthOutcome.Failure(AuthFailure.Unauthorized), result)
            }
        }

        val engine = MockEngine {
            respond(
                content =
                    """
                    {"status":422,"code":"EMAIL_BLOCKED","message":"private",
                     "timestamp":"synthetic","path":"/auth/refresh"}
                    """.trimIndent(),
                status = HttpStatusCode.UnprocessableEntity,
                headers = jsonHeaders,
            )
        }
        withClient(engine) { client ->
            val result = repository(client).refreshToken(refreshPermit)
            assertTrue(result is com.whysoezzy.auth.domain.models.RefreshOutcome.TransientFailure)
            val exception =
                (result as com.whysoezzy.auth.domain.models.RefreshOutcome.TransientFailure).error
            assertTrue(exception is ApiException.ServerError)
            assertEquals(422, (exception as ApiException.ServerError).metadata.status)
            assertEquals("EMAIL_BLOCKED", exception.metadata.code)
        }
    }

    @Test
    fun `malformed required response is rejected by repository`() = runTest {
        every { tokenManager.isLoggedInFlow } returns MutableStateFlow(false)
        val engine = MockEngine {
            respond(
                content = """{"accessToken":"access","refreshToken":"refresh","isNewUser":false}""",
                headers = jsonHeaders,
            )
        }

        withClient(engine) { client ->
            val result = repository(client).verifyEmailOtp("ada@example.com", "123456")
            assertEquals(AuthOutcome.Failure(AuthFailure.Unknown), result)
        }
    }

    @Test
    fun `IOException from real auth client maps to no connection`() = runTest {
        every { tokenManager.isLoggedInFlow } returns MutableStateFlow(false)
        val engine = MockEngine {
            throw IOException("synthetic transport detail")
        }

        withClient(engine) { client ->
            val result = repository(client).requestEmailOtp("ada@example.com")
            assertEquals(AuthOutcome.Failure(AuthFailure.NoConnection), result)
        }
    }

    private fun repository(client: HttpClient): AuthRepositoryImpl =
        AuthRepositoryImpl(AuthApiKtor(client), tokenManager)

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
            append(io.ktor.http.HttpHeaders.ContentType, ContentType.Application.Json.toString())
        }
    }
}
