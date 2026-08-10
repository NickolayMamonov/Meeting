package com.whysoezzy.domain.usecase

import androidx.paging.PagingData
import com.whysoezzy.domain.models.AdBlock
import com.whysoezzy.domain.models.Meeting
import com.whysoezzy.domain.models.Person
import com.whysoezzy.domain.repository.MeetingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class SearchMeetingsUseCaseTest {
    @Test
    fun `rethrows cancellation from repository`() = runTest {
        val cancellation = CancellationException("cancelled")
        val useCase = SearchMeetingsUseCase(
            FakeMeetingsRepository { throw cancellation },
        )

        try {
            useCase("kotlin")
            fail("CancellationException must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }

    @Test
    fun `maps ordinary repository failure to Result failure`() = runTest {
        val failure = IllegalStateException("offline")
        val useCase = SearchMeetingsUseCase(
            FakeMeetingsRepository { Result.failure(failure) },
        )

        val result = useCase("kotlin")

        assertEquals(failure, result.exceptionOrNull())
    }

    private class FakeMeetingsRepository(
        private val search: suspend (String) -> Result<List<Meeting>>,
    ) : MeetingsRepository {
        override suspend fun searchEvents(query: String): Result<List<Meeting>> = search(query)

        override suspend fun getHeroEvents(): Result<List<Meeting>> = error("Not used")

        override suspend fun getPopularEvents(): Result<List<Meeting>> = error("Not used")

        override suspend fun getAllEvents(
            page: Int,
            limit: Int,
            tagId: Long?,
        ): Result<List<Meeting>> = error("Not used")

        override fun getAllEventsPaged(tagId: Long?): Flow<PagingData<Meeting>> = error("Not used")

        override suspend fun getMeetingById(id: Long): Result<Meeting> = error("Not used")

        override suspend fun getMeetingParticipants(meetingId: Long): Result<List<Person>> = error("Not used")

        override suspend fun joinMeeting(meetingId: Long): Result<Unit> = error("Not used")

        override suspend fun leaveMeeting(meetingId: Long): Result<Unit> = error("Not used")

        override suspend fun getUserMeetings(): Result<List<Meeting>> = error("Not used")

        override suspend fun getAdBlocks(): Result<List<AdBlock>> = error("Not used")
    }
}
