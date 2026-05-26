package com.whysoezzy.domain.repository

import com.whysoezzy.domain.models.Community
import com.whysoezzy.domain.models.Meeting
import com.whysoezzy.domain.models.Person

interface CommunitiesRepository {
    suspend fun getRecommendedCommunities(): Result<List<Community>>
    suspend fun getCommunityById(id: Long): Result<Community>
    suspend fun subscribeToCommunity(id: Long): Result<Unit>
    suspend fun unsubscribeFromCommunity(id: Long): Result<Unit>
    suspend fun searchCommunities(query: String): Result<List<Community>>
    suspend fun getCommunityMeetings(id: Long): Result<List<Meeting>>
    suspend fun getCommunitySubscribers(id: Long): Result<List<Person>>
}