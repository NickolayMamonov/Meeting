package com.whysoezzy.data.repository

import android.util.Log
import com.whysoezzy.data.api.MeetingsApiImpl
import com.whysoezzy.data.mapper.MeetingMapper
import com.whysoezzy.domain.models.Meeting
import com.whysoezzy.domain.repository.MeetingsRepository
import com.whysoezzy.network.safeApiCall

class MeetingsRepositoryImpl(
    private val meetingsApi: MeetingsApiImpl,
    private val meetingMapper: MeetingMapper
) : MeetingsRepository {
    override suspend fun getHeroEvents(): Result<List<Meeting>> {
        return safeApiCall {
            val response = meetingsApi.getHeroEvents()
            response.map { meetingMapper.toDomain(it) }
        }
    }

    override suspend fun getPopularEvents(): Result<List<Meeting>> {
        return safeApiCall {
            val response = meetingsApi.getPopularEvents()
            response.map { meetingMapper.toDomain(it) }
        }
    }

    override suspend fun getAllEvents(
        page: Int,
        limit: Int
    ): Result<List<Meeting>> {
        return safeApiCall {
            val response = meetingsApi.getAllEvents(page = page, limit = limit)
            response.map { meetingMapper.toDomain(it) }
        }
    }

    override suspend fun searchEvents(query: String): Result<List<Meeting>> {
        return safeApiCall {
            val response = meetingsApi.searchEvents(query)
            response.map { meetingMapper.toDomain(it) }
        }
    }

    override suspend fun getMeetingById(id: Long): Result<Meeting> {
        return safeApiCall {
            val response = meetingsApi.getMeetingsById(id)
            meetingMapper.toDomain(response)
        }
    }

    override suspend fun joinMeeting(meetingId: Long): Result<Unit> {
        return safeApiCall {
            meetingsApi.joinMeeting(id = meetingId)
        }
    }

    override suspend fun leaveMeeting(meetingId: Long): Result<Unit> {
        return safeApiCall {
            meetingsApi.leaveMeeting(id = meetingId)
        }
    }

    override suspend fun getUserMeetings(): Result<List<Meeting>> {
        return safeApiCall {
            val response = meetingsApi.getUserMeetings()
            response.map { meetingMapper.toDomain(it) }
        }
    }

    override suspend fun getEventsByCategory(categoryId: Long): Result<List<Meeting>> {
        return safeApiCall {
            val response = meetingsApi.getEventsByCategory(categoryId)
            response.map { meetingMapper.toDomain(it) }
        }
    }

    override suspend fun getEventsByCommunity(communityId: Long): Result<List<Meeting>> {
        return safeApiCall {
            val response = meetingsApi.getEventsByCommunity(communityId = communityId)
            response.map { meetingMapper.toDomain(it) }
        }
    }
}