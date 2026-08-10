package com.whysoezzy.data.repository

import com.whysoezzy.data.adsJson
import com.whysoezzy.data.api.MeetingsApiKtor
import com.whysoezzy.data.errorJson
import com.whysoezzy.data.jsonHeaders
import com.whysoezzy.data.meetingJson
import com.whysoezzy.data.participantsJson
import com.whysoezzy.domain.models.AdBlock
import com.whysoezzy.domain.models.MeetingStatus
import com.whysoezzy.network.KtorNetworkModule
import com.whysoezzy.network.error.ApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetingsRepositoryContractTest {
    @Test
    fun `meeting detail deserializes and maps nested meeting contract`() = runTest {
        val engine = MockEngine {
            respond(content = meetingJson, headers = jsonHeaders)
        }

        withRepository(engine) { repository ->
            val meeting = repository.getMeetingById(42).getOrThrow()

            assertEquals(42L, meeting.id)
            assertEquals("1 Main Street", meeting.address.address)
            assertEquals(55.7558, meeting.address.latitude, 0.0)
            assertEquals(listOf("Kotlin", "Android"), meeting.tags.map { it.text })
            assertEquals("Ada", meeting.personHost?.name)
            assertEquals("Android Guild", meeting.communityHost?.title)
            assertEquals(
                "Compose",
                meeting.communityHost
                    ?.meetingsInfo
                    ?.single()
                    ?.title,
            )
            assertEquals("Grace", meeting.participants.single().name)
            assertEquals("", meeting.participants.single().avatarUrl)
            assertEquals("", meeting.participants.single().bio)
            assertTrue(
                meeting.participants
                    .single()
                    .role
                    .isNotBlank(),
            )
            assertEquals(MeetingStatus.FULL, meeting.meetingStatus)
            assertTrue(meeting.isUserInParticipants)
            assertEquals(0, meeting.capacity)
            assertEquals("TIMEPAD", meeting.source)
            assertEquals("https://events.example/42", meeting.externalUrl)
            assertTrue(meeting.isOnline)
        }
    }

    @Test
    fun `participants response maps every person field`() = runTest {
        val engine = MockEngine {
            respond(content = participantsJson, headers = jsonHeaders)
        }

        withRepository(engine) { repository ->
            val person = repository.getMeetingParticipants(42).getOrThrow().single()

            assertEquals(21L, person.id)
            assertEquals("Linus", person.name)
            assertEquals("Torvalds", person.surname)
            assertEquals("https://cdn.example/people/21.webp", person.avatarUrl)
            assertEquals("Kernel engineer", person.bio)
            assertEquals("SPEAKER", person.role)
        }
    }

    @Test
    fun `ad response deserializes and maps nested variants`() = runTest {
        val engine = MockEngine {
            respond(content = adsJson, headers = jsonHeaders)
        }

        withRepository(engine) { repository ->
            val ads = repository.getAdBlocks().getOrThrow()

            val communities = ads[0] as AdBlock.CommunitiesAd
            assertEquals("Kotlin User Group", communities.communities.single().name)
            assertEquals("", communities.communities.single().description)
            assertEquals(0, communities.communities.single().subscribersCount)
            assertTrue(communities.communities.single().isSubscribed)

            val text = ads[1] as AdBlock.TextAd
            assertEquals("Open", text.actionText)
            assertEquals("https://events.example/conf", text.actionUrl)
            assertFalse(text.isActive)

            val people = ads[2] as AdBlock.PeopleAd
            assertEquals("Barbara", people.users.single().name)
            assertEquals("SPEAKER", people.users.single().role)
        }
    }

    @Test
    fun `unauthorized statuses preserve status and backend code metadata`() = runTest {
        listOf(
            HttpStatusCode.Unauthorized to "AUTH_REQUIRED",
            HttpStatusCode.Forbidden to "MEETING_FORBIDDEN",
        ).forEach { (status, code) ->
            val engine = MockEngine {
                respond(
                    content = errorJson(status.value, code),
                    status = status,
                    headers = jsonHeaders,
                )
            }

            withRepository(engine) { repository ->
                val failure = repository.getMeetingById(42).exceptionOrNull()

                assertTrue(failure is ApiException.UnauthorizedError)
                val metadata = (failure as ApiException.UnauthorizedError).metadata
                assertEquals(status.value, metadata?.status)
                assertEquals(code, metadata?.code)
            }
        }
    }

    @Test
    fun `other backend failure preserves status and code metadata`() = runTest {
        val status = HttpStatusCode.UnprocessableEntity
        val engine = MockEngine {
            respond(
                content = errorJson(status.value, "MEETING_CLOSED"),
                status = status,
                headers = jsonHeaders,
            )
        }

        withRepository(engine) { repository ->
            val failure = repository.joinMeeting(42).exceptionOrNull()

            assertTrue(failure is ApiException.ServerError)
            val metadata = (failure as ApiException.ServerError).metadata
            assertEquals(status.value, metadata.status)
            assertEquals("MEETING_CLOSED", metadata.code)
        }
    }

    @Test
    fun `malformed meeting payload maps to unknown failure`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"id":42,"title":{"unexpected":"shape"}}""",
                headers = jsonHeaders,
            )
        }

        withRepository(engine) { repository ->
            val failure = repository.getMeetingById(42).exceptionOrNull()

            assertTrue(failure is ApiException.UnknownError)
        }
    }

    @Test
    fun `transport IOException maps to network failure`() = runTest {
        val engine = MockEngine {
            throw IOException("synthetic offline")
        }

        withRepository(engine) { repository ->
            val failure = repository.joinMeeting(42).exceptionOrNull()

            assertTrue(failure is ApiException.NetworkError)
            assertNull(failure?.cause)
        }
    }

    private suspend fun <T> withRepository(
        engine: MockEngine,
        block: suspend (MeetingsRepositoryImpl) -> T,
    ): T {
        val client: HttpClient = KtorNetworkModule.provideHttpClient(engine)
        return try {
            block(MeetingsRepositoryImpl(MeetingsApiKtor(client)))
        } finally {
            client.close()
        }
    }
}
