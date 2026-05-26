package com.whysoezzy.data.repository

import com.whysoezzy.data.api.MeetingsApi
import com.whysoezzy.data.mapper.toDomain
import com.whysoezzy.domain.models.AdBlock
import com.whysoezzy.domain.models.Meeting
import com.whysoezzy.domain.models.Person
import com.whysoezzy.domain.repository.MeetingsRepository
import com.whysoezzy.network.safeApiCall

class MeetingsRepositoryImpl(
    private val meetingsApi: MeetingsApi
) : MeetingsRepository {

    override suspend fun getHeroEvents(): Result<List<Meeting>> = safeApiCall {
        meetingsApi.getHeroEvents().map { it.toDomain() }
    }

    override suspend fun getPopularEvents(): Result<List<Meeting>> = safeApiCall {
        meetingsApi.getPopularEvents().map { it.toDomain() }
    }

    // TODO: реализовать пагинацию через Paging 3 когда объём данных потребует этого
    override suspend fun getAllEvents(page: Int, limit: Int, tagId: Long?): Result<List<Meeting>> = safeApiCall {
        meetingsApi.getAllEvents(page = page, limit = limit, tagId = tagId).map { it.toDomain() }

    }

    override suspend fun searchEvents(query: String): Result<List<Meeting>> = safeApiCall {
        meetingsApi.searchEvents(query).map { it.toDomain() }
    }

    override suspend fun getMeetingById(id: Long): Result<Meeting> = safeApiCall {
        meetingsApi.getMeetingsById(id).toDomain()
    }

    override suspend fun getMeetingParticipants(meetingId: Long): Result<List<Person>> = safeApiCall {
        meetingsApi.getMeetingParticipants(meetingId).map { dto ->
            Person(
                id = dto.id,
                name = dto.name,
                surname = dto.surname,
                avatarUrl = dto.avatarUrl,
                bio = dto.bio,
                role = dto.role
            )
        }
    }

    override suspend fun joinMeeting(meetingId: Long): Result<Unit> = safeApiCall {
        meetingsApi.joinMeeting(id = meetingId)
    }

    override suspend fun leaveMeeting(meetingId: Long): Result<Unit> = safeApiCall {
        meetingsApi.leaveMeeting(id = meetingId)
    }

    override suspend fun getUserMeetings(): Result<List<Meeting>> = safeApiCall {
        meetingsApi.getUserMeetings().map { it.toDomain() }
    }

    override suspend fun getAdBlocks(): Result<List<AdBlock>> = safeApiCall {
        val raw = meetingsApi.getAdBlocks()
        val mapped = raw.map { it.toDomain() }
        mapped
    }
}
