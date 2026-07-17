package com.whysoezzy.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class KtorNetworkModuleTest {
    @Test
    fun `failed token refresh does not retry protected request or recurse`() =
        runTest {
            val requestCount = AtomicInteger()
            val refreshCount = AtomicInteger()
            val engine = MockEngine { request ->
                requestCount.incrementAndGet()
                assertEquals("Bearer old-access-token", request.headers[HttpHeaders.Authorization])
                respond(content = "", status = HttpStatusCode.Unauthorized)
            }
            val client =
                KtorNetworkModule.provideHttpClient(
                    engine = engine,
                    tokenProvider =
                        object : TokenProvider {
                            override suspend fun getAccessToken() = "old-access-token"

                            override suspend fun getRefreshToken() = "old-refresh-token"
                        },
                    onRefreshToken = {
                        refreshCount.incrementAndGet()
                        null
                    },
                )

            try {
                val response = client.get("/protected").bodyAsText()

                assertEquals("", response)
                assertEquals(1, requestCount.get())
                assertEquals(1, refreshCount.get())
            } finally {
                client.close()
            }
        }

    @Test
    fun `concurrent unauthorized requests share one token refresh and retry with new bearer token`() =
        runTest {
            val initialRequestsEntered = CompletableDeferred<Unit>()
            val initialRequestCount = AtomicInteger()
            val refreshCount = AtomicInteger()
            val retriedAuthorizationHeaders = ConcurrentLinkedQueue<String?>()

            val engine = MockEngine { request ->
                when (request.headers[HttpHeaders.Authorization]) {
                    "Bearer old-access-token" -> {
                        if (initialRequestCount.incrementAndGet() == 2) {
                            initialRequestsEntered.complete(Unit)
                        }
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

            val client =
                KtorNetworkModule.provideHttpClient(
                    engine = engine,
                    tokenProvider =
                        object : TokenProvider {
                            override suspend fun getAccessToken() = "old-access-token"

                            override suspend fun getRefreshToken() = "old-refresh-token"
                        },
                    onRefreshToken = {
                        refreshCount.incrementAndGet()
                        initialRequestsEntered.await()
                        "new-access-token" to "new-refresh-token"
                    },
                )

            try {
                val responses =
                    awaitAll(
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
                assertTrue(retriedAuthorizationHeaders.all { it == "Bearer new-access-token" })
            } finally {
                client.close()
            }
        }
}
