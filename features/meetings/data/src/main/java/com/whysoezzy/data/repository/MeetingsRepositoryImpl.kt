package com.whysoezzy.data.repository

import com.whysoezzy.data.api.MeetingsApiImpl
import com.whysoezzy.data.mapper.MeetingMapper
import com.whysoezzy.data.mapper.toDomain
import com.whysoezzy.domain.models.AdBlock
import com.whysoezzy.domain.models.Meeting
import com.whysoezzy.domain.models.Person
import com.whysoezzy.domain.repository.MeetingsRepository
import com.whysoezzy.network.safeApiCall

class MeetingsRepositoryImpl(
    private val meetingsApi: MeetingsApiImpl,
    private val meetingMapper: MeetingMapper,
) : MeetingsRepository {

    override suspend fun getHeroEvents(): Result<List<Meeting>> = safeApiCall {
        meetingsApi.getHeroEvents().map { meetingMapper.toDomain(it) }
    }

    override suspend fun getPopularEvents(): Result<List<Meeting>> = safeApiCall {
        meetingsApi.getPopularEvents().map { meetingMapper.toDomain(it) }
    }

    override suspend fun getAllEvents(page: Int, limit: Int, tagId: Long?): Result<List<Meeting>> = safeApiCall {
        meetingsApi.getAllEvents(page = page, limit = limit, tagId = tagId)
            .map { meetingMapper.toDomain(it) }
    }

    override suspend fun searchEvents(query: String): Result<List<Meeting>> = safeApiCall {
        meetingsApi.searchEvents(query).map { meetingMapper.toDomain(it) }
    }

    override suspend fun getMeetingById(id: Long): Result<Meeting> = safeApiCall {
        meetingMapper.toDomain(meetingsApi.getMeetingsById(id))
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
        meetingsApi.getUserMeetings().map { meetingMapper.toDomain(it) }
    }

    override suspend fun getEventsByCommunity(communityId: Long): Result<List<Meeting>> = safeApiCall {
        meetingsApi.getEventsByCommunity(communityId = communityId)
            .map { meetingMapper.toDomain(it) }
    }

    override suspend fun getAdBlocks(): Result<List<AdBlock>> = safeApiCall {
        val raw = meetingsApi.getAdBlocks()
        android.util.Log.d("MeetingsRepo", "Raw adBlocks from API: ${raw.size} items")
        raw.forEach { dto ->
            android.util.Log.d("MeetingsRepo", "  AdBlock dto: id=${dto.id}, type=${dto.type}, title=${dto.title}")
        }
        val mapped = raw.map { it.toDomain() }
        android.util.Log.d("MeetingsRepo", "Mapped adBlocks: ${mapped.size} items")
        mapped
    }
}
