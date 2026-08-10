package com.whysoezzy.data.api

import com.whysoezzy.data.jsonHeaders
import com.whysoezzy.data.participantsJson
import com.whysoezzy.network.KtorNetworkModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetingsApiKtorTest {
    @Test
    fun `get all meetings sends observable paging and tag query`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/meetings", request.url.encodedPath)
            assertEquals("3", request.url.parameters["page"])
            assertEquals("40", request.url.parameters["limit"])
            assertEquals("17", request.url.parameters["tagId"])
            respond(content = "[]", headers = jsonHeaders)
        }

        withApi(engine) { api ->
            assertTrue(api.getAllEvents(page = 3, limit = 40, tagId = 17).isEmpty())
        }
    }

    @Test
    fun `search meetings preserves query while encoding reserved characters`() = runTest {
        val query = "Kotlin & Compose/mobile?"
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/meetings/search", request.url.encodedPath)
            assertEquals(query, request.url.parameters["query"])
            assertFalse(request.url.encodedQuery.contains(" "))
            assertTrue(request.url.encodedQuery.contains("%26"))
            respond(content = "[]", headers = jsonHeaders)
        }

        withApi(engine) { api ->
            assertTrue(api.searchEvents(query).isEmpty())
        }
    }

    @Test
    fun `meeting participants sends GET to meeting participants path`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/meetings/42/participants", request.url.encodedPath)
            respond(content = participantsJson, headers = jsonHeaders)
        }

        withApi(engine) { api ->
            assertEquals(21L, api.getMeetingParticipants(42).single().id)
        }
    }

    @Test
    fun `join meeting sends POST to join path`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/meetings/42/join", request.url.encodedPath)
            assertEquals(ContentType.Application.Json.toString(), request.headers[HttpHeaders.ContentType])
            respond(content = "")
        }

        withApi(engine) { api ->
            api.joinMeeting(42)
        }
    }

    @Test
    fun `leave meeting sends DELETE to leave path`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("/meetings/42/leave", request.url.encodedPath)
            respond(content = "")
        }

        withApi(engine) { api ->
            api.leaveMeeting(42)
        }
    }

    @Test
    fun `ad blocks sends GET to api ads path`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/ads", request.url.encodedPath)
            respond(content = "[]", headers = jsonHeaders)
        }

        withApi(engine) { api ->
            assertTrue(api.getAdBlocks().isEmpty())
        }
    }

    private suspend fun <T> withApi(
        engine: MockEngine,
        block: suspend (MeetingsApiKtor) -> T,
    ): T {
        val client: HttpClient = KtorNetworkModule.provideHttpClient(engine)
        return try {
            block(MeetingsApiKtor(client))
        } finally {
            client.close()
        }
    }
}
