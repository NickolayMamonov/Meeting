package com.whysoezzy.data.api

import com.whysoezzy.data.dto.AdBlockResponseDto
import com.whysoezzy.data.dto.MeetingDto
import com.whysoezzy.data.dto.UserInfoDto

internal interface MeetingsApi {
    suspend fun getHeroEvents(): List<MeetingDto>

    suspend fun getPopularEvents(): List<MeetingDto>

    suspend fun getAllEvents(page: Int = 0, limit: Int = 20, tagId: Long? = null): List<MeetingDto>

    suspend fun searchEvents(query: String): List<MeetingDto>

    suspend fun getMeetingsById(id: Long): MeetingDto

    suspend fun getMeetingParticipants(id: Long): List<UserInfoDto>

    suspend fun joinMeeting(id: Long)

    suspend fun leaveMeeting(id: Long)

    suspend fun getUserMeetings(): List<MeetingDto>

    suspend fun getAdBlocks(): List<AdBlockResponseDto>
}
