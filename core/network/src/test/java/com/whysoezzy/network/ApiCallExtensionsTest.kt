package com.whysoezzy.network

import com.whysoezzy.network.error.ApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiCallExtensionsTest {
    @Test
    fun `server error exposes only sanitized metadata`() =
        runTest {
            val secret = "person@example.com bearer-secret raw-server-message"
            val client =
                HttpClient(
                    MockEngine {
                        respond(
                            content =
                                """
                                {
                                  "status": 500,
                                  "message": "$secret",
                                  "timestamp": "private-timestamp",
                                  "path": "/auth/email/verify?email=person@example.com"
                                }
                                """.trimIndent(),
                            status = HttpStatusCode.InternalServerError,
                        )
                    },
                ) {
                    expectSuccess = true
                }

            try {
                val exception =
                    safeApiCall {
                        client.get("https://example.test/auth/email/verify").bodyAsText()
                    }.exceptionOrNull()

                assertTrue(exception is ApiException.ServerError)
                val serverError = exception as ApiException.ServerError
                assertEquals(500, serverError.metadata.status)
                assertEquals("Server request failed", serverError.message)
                assertSanitized(serverError, secret)
            } finally {
                client.close()
            }
        }

    @Test
    fun `unauthorized error does not expose response body`() =
        runTest {
            val secret = "person@example.com bearer-secret"
            val client =
                HttpClient(
                    MockEngine {
                        respond(
                            content = """{"message":"$secret"}""",
                            status = HttpStatusCode.Unauthorized,
                        )
                    },
                ) {
                    expectSuccess = true
                }

            try {
                val exception =
                    safeApiCall {
                        client.get("https://example.test/private").bodyAsText()
                    }.exceptionOrNull()

                assertTrue(exception is ApiException.UnauthorizedError)
                assertSanitized(exception as ApiException, secret)
            } finally {
                client.close()
            }
        }

    @Test
    fun `network and unknown errors do not expose cause messages`() =
        runTest {
            val secret = "person@example.com bearer-secret"

            val networkError =
                safeApiCall<Unit> {
                    throw IOException(secret)
                }.exceptionOrNull()
            val unknownError =
                safeApiCall<Unit> {
                    throw IllegalStateException(secret)
                }.exceptionOrNull()

            assertTrue(networkError is ApiException.NetworkError)
            assertEquals("Network request failed", networkError?.message)
            assertSanitized(networkError as ApiException, secret)
            assertTrue(unknownError is ApiException.UnknownError)
            assertEquals("Unexpected request failure", unknownError?.message)
            assertSanitized(unknownError as ApiException, secret)
        }

    private fun assertSanitized(
        exception: ApiException,
        secret: String,
    ) {
        assertFalse(exception.toString().contains(secret))
        assertFalse(exception.message.orEmpty().contains(secret))
        assertFalse(exception.stackTraceToString().contains(secret))
    }
}
