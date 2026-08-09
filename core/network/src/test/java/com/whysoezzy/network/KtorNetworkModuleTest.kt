package com.whysoezzy.network

import com.whysoezzy.network.error.ApiException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class KtorNetworkModuleTest {
    @Test
    fun `email auth request is sent without bearer token or retries`() = runTest {
        val requestCount = AtomicInteger()
        val engine = MockEngine { request ->
            requestCount.incrementAndGet()
            assertNull(request.headers[HttpHeaders.Authorization])
            respond(content = "{\"message\":\"try later\"}", status = HttpStatusCode.InternalServerError)
        }
        val client = KtorNetworkModule.provideHttpClient(
            engine = engine,
            tokenProvider = tokenProvider(),
            onRefreshToken = { "new-access-token" to "new-refresh-token" },
        )
        try {
            val result = safeApiCall { client.post("/auth/email/send-otp").bodyAsText() }
            assertTrue(result.exceptionOrNull() is ApiException.ServerError)
            assertEquals(1, requestCount.get())
        } finally {
            client.close()
        }
    }

    @Test
    fun `non idempotent request is not retried`() = runTest {
        val requestCount = AtomicInteger()
        val engine = MockEngine {
            requestCount.incrementAndGet()
            respond(content = "", status = HttpStatusCode.InternalServerError)
        }
        val client = KtorNetworkModule.provideHttpClient(engine)
        try {
            safeApiCall { client.post("/meetings").bodyAsText() }
            assertEquals(1, requestCount.get())
        } finally {
            client.close()
        }
    }

    @Test
    fun `idempotent request still retries server errors`() = runTest {
        val requestCount = AtomicInteger()
        val engine = MockEngine {
            val attempt = requestCount.incrementAndGet()
            if (attempt < 4) {
                respond(content = "", status = HttpStatusCode.InternalServerError)
            } else {
                respond(content = "success", status = HttpStatusCode.OK)
            }
        }
        val client = KtorNetworkModule.provideHttpClient(engine)
        try {
            assertEquals("success", client.get("/meetings").bodyAsText())
            assertEquals(4, requestCount.get())
        } finally {
            client.close()
        }
    }

    @Test
    fun `failed token refresh does not retry protected request or recurse`() = runTest {
        val requestCount = AtomicInteger()
        val refreshCount = AtomicInteger()
        val engine = MockEngine { request ->
            requestCount.incrementAndGet()
            assertEquals("Bearer old-access-token", request.headers[HttpHeaders.Authorization])
            respond(content = "", status = HttpStatusCode.Unauthorized)
        }
        val client = KtorNetworkModule.provideHttpClient(
            engine = engine,
            tokenProvider = tokenProvider(),
            onRefreshToken = {
                refreshCount.incrementAndGet()
                null
            },
        )
        try {
            val result = safeApiCall { client.get("/protected").bodyAsText() }
            assertTrue(result.isFailure)
            assertEquals(1, requestCount.get())
            assertEquals(1, refreshCount.get())
        } finally {
            client.close()
        }
    }

    @Test
    fun `concurrent unauthorized requests share one token refresh and retry with new bearer token`() = runTest {
        val initialRequestsEntered = CompletableDeferred<Unit>()
        val initialRequestCount = AtomicInteger()
        val refreshCount = AtomicInteger()
        val retriedAuthorizationHeaders = ConcurrentLinkedQueue<String?>()
        val engine = MockEngine { request ->
            when (request.headers[HttpHeaders.Authorization]) {
                "Bearer old-access-token" -> {
                    if (initialRequestCount.incrementAndGet() == 2) initialRequestsEntered.complete(Unit)
                    initialRequestsEntered.await()
                    respond(content = "", status = HttpStatusCode.Unauthorized)
                }
                "Bearer new-access-token" -> {
                    retriedAuthorizationHeaders.add(request.headers[HttpHeaders.Authorization])
                    respond(content = "success", status = HttpStatusCode.OK)
                }
                else -> error("Unexpected authorization header: ${request.headers[HttpHeaders.Authorization]}")
            }
        }
        val client = KtorNetworkModule.provideHttpClient(
            engine = engine,
            tokenProvider = tokenProvider(),
            onRefreshToken = {
                refreshCount.incrementAndGet()
                initialRequestsEntered.await()
                "new-access-token" to "new-refresh-token"
            },
        )
        try {
            val responses = awaitAll(
                async { client.get("/protected/one").bodyAsText() },
                async { client.get("/protected/two").bodyAsText() },
            )
            assertEquals(listOf("success", "success"), responses)
            assertEquals(1, refreshCount.get())
            assertEquals(2, initialRequestCount.get())
            assertEquals(
                listOf("Bearer new-access-token", "Bearer new-access-token"),
                retriedAuthorizationHeaders.toList(),
            )
        } finally {
            client.close()
        }
    }

    private fun tokenProvider(
        accessToken: String = "old-access-token",
        refreshToken: String = "old-refresh-token",
    ): TokenProvider = object : TokenProvider {
        override suspend fun getAccessToken() = accessToken

        override suspend fun getRefreshToken() = refreshToken

        override suspend fun loadTokens() = TokenSnapshot(accessToken, refreshToken)
    }
}
