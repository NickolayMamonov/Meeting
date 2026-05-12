package com.whysoezzy.domain.repository

import com.whysoezzy.domain.models.AdBlock
import com.whysoezzy.domain.models.Meeting
import com.whysoezzy.domain.models.Person

interface MeetingsRepository {
    suspend fun getHeroEvents(): Result<List<Meeting>>
    suspend fun getPopularEvents(): Result<List<Meeting>>
    suspend fun getAllEvents(page: Int = 0, limit: Int = 20, tagId: Long? = null): Result<List<Meeting>>
    suspend fun searchEvents(query: String): Result<List<Meeting>>
    suspend fun getMeetingById(id: Long): Result<Meeting>
    suspend fun getMeetingParticipants(meetingId: Long): Result<List<Person>>
    suspend fun joinMeeting(meetingId: Long): Result<Unit>
    suspend fun leaveMeeting(meetingId: Long): Result<Unit>
    suspend fun getUserMeetings(): Result<List<Meeting>>
    suspend fun getAdBlocks(): Result<List<AdBlock>>
}
