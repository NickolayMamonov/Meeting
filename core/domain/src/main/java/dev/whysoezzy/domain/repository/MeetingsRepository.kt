package dev.whysoezzy.domain.repository

import dev.whysoezzy.domain.models.Meeting

interface MeetingsRepository {
    suspend fun getHeroEvents(): Result<List<Meeting>>
    suspend fun getPopularEvents(): Result<List<Meeting>>
    suspend fun getAllEvents(page: Int = 0, limit: Int = 20): Result<List<Meeting>>
    suspend fun searchEvents(query: String): Result<List<Meeting>>
    suspend fun getMeetingById(id: Long): Result<Meeting>

    suspend fun joinMeeting(meetingId: Long): Result<Unit>
    suspend fun leaveMeeting(meetingId: Long): Result<Unit>
    suspend fun getUserMeetings(): Result<List<Meeting>>
    suspend fun getEventsByCategory(categoryId: Long): Result<List<Meeting>>
    suspend fun getEventsByCommunity(communityId: Long): Result<List<Meeting>>
}