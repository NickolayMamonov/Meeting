package com.whysoezzy.network

import com.whysoezzy.network.error.ApiException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class KtorNetworkModuleTest {
    @Test
    fun `email auth request is sent without bearer token or retries`() =
        runTest {
            val requestCount = AtomicInteger()
            val engine =
                MockEngine { request ->
                    requestCount.incrementAndGet()
                    assertNull(request.headers[HttpHeaders.Authorization])
                    respond(
                        content = """{"message":"try later"}""",
                        status = HttpStatusCode.InternalServerError,
                    )
                }
            val client =
                KtorNetworkModule.provideHttpClient(
                    engine = engine,
                    tokenProvider = tokenProvider(),
                    onRefreshToken = { "new-access-token" to "new-refresh-token" },
                )

            try {
                val result =
                    safeApiCall {
                        client.post("/auth/email/send-otp").bodyAsText()
                    }

                assertTrue(result.exceptionOrNull() is ApiException.ServerError)
                assertEquals(1, requestCount.get())
            } finally {
                client.close()
            }
        }

    @Test
    fun `non idempotent request is not retried`() =
        runTest {
            val requestCount = AtomicInteger()
            val engine =
                MockEngine {
                    requestCount.incrementAndGet()
                    respond(content = "", status = HttpStatusCode.InternalServerError)
                }
            val client = KtorNetworkModule.provideHttpClient(engine)

            try {
                safeApiCall {
                    client.post("/meetings").bodyAsText()
                }

                assertEquals(1, requestCount.get())
            } finally {
                client.close()
            }
        }

    @Test
    fun `idempotent request still retries server errors`() =
        runTest {
            val requestCount = AtomicInteger()
            val engine =
                MockEngine {
                    val attempt = requestCount.incrementAndGet()
                    if (attempt < 4) {
                        respond(content = "", status = HttpStatusCode.InternalServerError)
                    } else {
                        respond(content = "success", status = HttpStatusCode.OK)
                    }
                }
            val client = KtorNetworkModule.provideHttpClient(engine)

            try {
                val response = client.get("/meetings").bodyAsText()

                assertEquals("success", response)
                assertEquals(4, requestCount.get())
            } finally {
                client.close()
            }
        }

    private fun tokenProvider(): TokenProvider =
        object : TokenProvider {
            override suspend fun getAccessToken() = "access-token"

            override suspend fun getRefreshToken() = "refresh-token"

            override suspend fun loadTokens() = TokenSnapshot("access-token", "refresh-token")
        }
}
