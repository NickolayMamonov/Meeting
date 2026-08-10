package com.whysoezzy.data.api

import com.whysoezzy.data.CommunitiesTestFixtures
import com.whysoezzy.network.KtorNetworkModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunitiesApiKtorTest {
    private val jsonHeaders = Headers.build {
        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }

    @Test
    fun `recommended and detail deserialize nested communities`() = runTest {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            requests += request.method to request.url.encodedPath
            val content = when (request.url.encodedPath) {
                "/communities/recommended" -> "[${CommunitiesTestFixtures.communityJson}]"
                "/communities/${CommunitiesTestFixtures.COMMUNITY_ID}" ->
                    CommunitiesTestFixtures.communityJson
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
            respond(content = content, headers = jsonHeaders)
        }

        withClient(engine) { client ->
            val api = CommunitiesApiKtor(client)
            val recommended = api.getRecommendedCommunities().single()
            val detail = api.getCommunityById(CommunitiesTestFixtures.COMMUNITY_ID)

            assertEquals("Compiler Club", recommended.name)
            assertEquals(7L, recommended.tags.single().id)
            assertEquals("Kotlin", recommended.tags.single().name)
            assertEquals(128, detail.subscribersCount)
            assertTrue(detail.isSubscribed)
        }

        assertEquals(
            listOf(
                HttpMethod.Get to "/communities/recommended",
                HttpMethod.Get to "/communities/${CommunitiesTestFixtures.COMMUNITY_ID}",
            ),
            requests,
        )
    }

    @Test
    fun `search percent-encodes reserved query characters`() = runTest {
        var encodedQuery = ""
        var decodedQuery = ""
        val engine = MockEngine { request ->
            encodedQuery = request.url.encodedQuery
            decodedQuery = request.url.parameters["query"].orEmpty()
            respond(content = "[]", headers = jsonHeaders)
        }

        withClient(engine) { client ->
            CommunitiesApiKtor(client).searchCommunities("Kotlin & Android/RU?")
        }

        assertEquals("Kotlin & Android/RU?", decodedQuery)
        assertTrue(encodedQuery.startsWith("query="))
        assertTrue(encodedQuery.contains("%26"))
        assertTrue(encodedQuery.contains("%2F"))
        assertTrue(encodedQuery.contains("%3F"))
        assertTrue(!encodedQuery.contains(" & "))
    }

    @Test
    fun `related meetings and subscribers deserialize nested payloads`() = runTest {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            requests += request.method to request.url.encodedPath
            val content = when (request.url.encodedPath) {
                "/communities/${CommunitiesTestFixtures.COMMUNITY_ID}/meetings" ->
                    "[${CommunitiesTestFixtures.meetingJson}]"
                "/communities/${CommunitiesTestFixtures.COMMUNITY_ID}/subscribers" ->
                    "[${CommunitiesTestFixtures.subscriberJson}]"
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
            respond(content = content, headers = jsonHeaders)
        }

        withClient(engine) { client ->
            val api = CommunitiesApiKtor(client)
            val meeting = api.getCommunityMeetings(CommunitiesTestFixtures.COMMUNITY_ID).single()
            val subscriber = api.getCommunitySubscribers(CommunitiesTestFixtures.COMMUNITY_ID).single()

            assertEquals("Test Hall", meeting.address.address)
            assertEquals("Ada", meeting.personHost?.name)
            assertEquals("Compiler Club", meeting.communityHost?.title)
            assertEquals("Grace", meeting.participants.single().name)
            assertEquals("Grace Hopper", "${subscriber.name} ${subscriber.surname}")
        }

        assertEquals(
            listOf(
                HttpMethod.Get to "/communities/${CommunitiesTestFixtures.COMMUNITY_ID}/meetings",
                HttpMethod.Get to "/communities/${CommunitiesTestFixtures.COMMUNITY_ID}/subscribers",
            ),
            requests,
        )
    }

    @Test
    fun `subscribe and unsubscribe use mutation HTTP contracts`() = runTest {
        val requests = mutableListOf<io.ktor.client.request.HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            respond(content = "", status = HttpStatusCode.NoContent)
        }

        withClient(engine) { client ->
            val api = CommunitiesApiKtor(client)
            api.subscribeToCommunity(CommunitiesTestFixtures.COMMUNITY_ID)
            api.unsubscribeFromCommunity(CommunitiesTestFixtures.COMMUNITY_ID)
        }

        assertEquals(
            listOf(HttpMethod.Post, HttpMethod.Delete),
            requests.map { it.method },
        )
        assertEquals(
            listOf(
                "/communities/${CommunitiesTestFixtures.COMMUNITY_ID}/subscribe",
                "/communities/${CommunitiesTestFixtures.COMMUNITY_ID}/subscribe",
            ),
            requests.map { it.url.encodedPath },
        )
        assertTrue(
            requests
                .first()
                .headers[HttpHeaders.ContentType]
                .orEmpty()
                .startsWith(ContentType.Application.Json.toString()),
        )
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
}
