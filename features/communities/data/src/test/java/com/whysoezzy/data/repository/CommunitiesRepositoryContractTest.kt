package com.whysoezzy.data.repository

import com.whysoezzy.data.CommunitiesTestFixtures
import com.whysoezzy.data.api.CommunitiesApiKtor
import com.whysoezzy.domain.models.MeetingStatus
import com.whysoezzy.domain.models.TagState
import com.whysoezzy.network.KtorNetworkModule
import com.whysoezzy.network.error.ApiException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunitiesRepositoryContractTest {
    private val jsonHeaders = Headers.build {
        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }

    @Test
    fun `community responses map to the domain contract`() = runTest {
        val engine = MockEngine { request ->
            val content = when (request.url.encodedPath) {
                "/communities/recommended" -> "[${CommunitiesTestFixtures.communityJson}]"
                "/communities/${CommunitiesTestFixtures.COMMUNITY_ID}" ->
                    CommunitiesTestFixtures.communityJson
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
            respond(content = content, headers = jsonHeaders)
        }

        withRepository(engine) { repository ->
            val recommended = repository.getRecommendedCommunities().getOrThrow().single()
            val detail = repository
                .getCommunityById(CommunitiesTestFixtures.COMMUNITY_ID)
                .getOrThrow()

            assertEquals(CommunitiesTestFixtures.COMMUNITY_ID, recommended.id)
            assertEquals("Compiler Club", recommended.name)
            assertEquals("A synthetic community", recommended.description)
            assertEquals("https://example.test/community.png", recommended.imageUrl)
            assertEquals(128, recommended.subscribersCount)
            assertTrue(recommended.isSubscribed)
            assertEquals(7L, recommended.tags.single().id)
            assertEquals("Kotlin", recommended.tags.single().name)
            assertEquals(recommended, detail)
        }
    }

    @Test
    fun `related meeting and subscriber responses map to domain contracts`() = runTest {
        val engine = MockEngine { request ->
            val content = when (request.url.encodedPath) {
                "/communities/${CommunitiesTestFixtures.COMMUNITY_ID}/meetings" ->
                    "[${CommunitiesTestFixtures.meetingJson}]"
                "/communities/${CommunitiesTestFixtures.COMMUNITY_ID}/subscribers" ->
                    "[${CommunitiesTestFixtures.subscriberJson}]"
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
            respond(content = content, headers = jsonHeaders)
        }

        withRepository(engine) { repository ->
            val meeting = repository
                .getCommunityMeetings(CommunitiesTestFixtures.COMMUNITY_ID)
                .getOrThrow()
                .single()
            val subscriber = repository
                .getCommunitySubscribers(CommunitiesTestFixtures.COMMUNITY_ID)
                .getOrThrow()
                .single()

            assertEquals(73L, meeting.id)
            assertEquals("Test Hall", meeting.address.address)
            assertEquals(55.75, meeting.address.latitude, 0.0)
            assertEquals("Compilers", meeting.tags.single().text)
            assertEquals(TagState.ACTIVE, meeting.tags.single().state)
            assertEquals("Ada Lovelace", "${meeting.personHost?.name} ${meeting.personHost?.surname}")
            assertEquals("Compiler Club", meeting.communityHost?.title)
            assertEquals(
                "Next compiler meeting",
                meeting.communityHost
                    ?.meetingsInfo
                    ?.single()
                    ?.title,
            )
            assertEquals("Grace", meeting.participants.single().name)
            assertEquals(MeetingStatus.COMPLETED, meeting.meetingStatus)
            assertEquals(0, meeting.capacity)
            assertEquals("EXTERNAL", meeting.source)
            assertTrue(meeting.isOnline)

            assertEquals(12L, subscriber.id)
            assertEquals("Grace Hopper", "${subscriber.name} ${subscriber.surname}")
            assertEquals("https://example.test/grace.png", subscriber.avatarUrl)
            assertEquals("Participant bio", subscriber.bio)
            assertEquals("MEMBER", subscriber.role)
        }
    }

    @Test
    fun `successful subscribe and unsubscribe mutations return success`() = runTest {
        val methods = mutableListOf<HttpMethod>()
        val engine = MockEngine { request ->
            methods += request.method
            respond(content = "", status = HttpStatusCode.NoContent)
        }

        withRepository(engine) { repository ->
            assertTrue(repository.subscribeToCommunity(CommunitiesTestFixtures.COMMUNITY_ID).isSuccess)
            assertTrue(repository.unsubscribeFromCommunity(CommunitiesTestFixtures.COMMUNITY_ID).isSuccess)
        }

        assertEquals(listOf(HttpMethod.Post, HttpMethod.Delete), methods)
    }

    @Test
    fun `401 and 403 envelopes map to unauthorized with backend metadata`() = runTest {
        listOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden).forEach { status ->
            val code = "AUTH_${status.value}"
            val engine = MockEngine {
                respond(
                    content = CommunitiesTestFixtures.errorEnvelope(status.value, code),
                    status = status,
                    headers = jsonHeaders,
                )
            }

            withRepository(engine) { repository ->
                val error = repository
                    .getCommunityById(CommunitiesTestFixtures.COMMUNITY_ID)
                    .exceptionOrNull()

                assertTrue(error is ApiException.UnauthorizedError)
                val unauthorized = error as ApiException.UnauthorizedError
                assertEquals(status.value, unauthorized.metadata?.status)
                assertEquals(code, unauthorized.metadata?.code)
            }
        }
    }

    @Test
    fun `other backend envelope maps to server error with status and code`() = runTest {
        val engine = MockEngine {
            respond(
                content = CommunitiesTestFixtures.errorEnvelope(422, "COMMUNITY_ARCHIVED"),
                status = HttpStatusCode.UnprocessableEntity,
                headers = jsonHeaders,
            )
        }

        withRepository(engine) { repository ->
            val error = repository
                .subscribeToCommunity(CommunitiesTestFixtures.COMMUNITY_ID)
                .exceptionOrNull()

            assertTrue(error is ApiException.ServerError)
            val serverError = error as ApiException.ServerError
            assertEquals(422, serverError.metadata.status)
            assertEquals("COMMUNITY_ARCHIVED", serverError.metadata.code)
        }
    }

    @Test
    fun `malformed success payload maps to unknown error`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"id":"not-a-number"}""",
                headers = jsonHeaders,
            )
        }

        withRepository(engine) { repository ->
            val error = repository
                .getCommunityById(CommunitiesTestFixtures.COMMUNITY_ID)
                .exceptionOrNull()

            assertTrue(error is ApiException.UnknownError)
        }
    }

    @Test
    fun `IOException transport failure maps to network error`() = runTest {
        val engine = MockEngine {
            throw IOException("synthetic offline")
        }

        withRepository(engine) { repository ->
            val error = repository
                .getRecommendedCommunities()
                .exceptionOrNull()

            assertTrue(error is ApiException.NetworkError)
        }
    }

    private suspend fun <T> withRepository(
        engine: MockEngine,
        block: suspend (CommunitiesRepositoryImpl) -> T,
    ): T {
        val client = KtorNetworkModule.provideHttpClient(engine)
        return try {
            block(CommunitiesRepositoryImpl(CommunitiesApiKtor(client)))
        } finally {
            client.close()
        }
    }
}
