package com.whysoezzy.data.api

import com.whysoezzy.data.dto.CommunityDto
import com.whysoezzy.data.dto.MeetingDto
import com.whysoezzy.data.dto.UserInfoDto

internal interface CommunitiesApi {
    suspend fun getRecommendedCommunities(): List<CommunityDto>

    suspend fun getCommunityById(id: Long): CommunityDto

    suspend fun subscribeToCommunity(id: Long)

    suspend fun unsubscribeFromCommunity(id: Long)

    suspend fun searchCommunities(query: String): List<CommunityDto>

    suspend fun getCommunityMeetings(id: Long): List<MeetingDto>

    suspend fun getCommunitySubscribers(id: Long): List<UserInfoDto>
}
