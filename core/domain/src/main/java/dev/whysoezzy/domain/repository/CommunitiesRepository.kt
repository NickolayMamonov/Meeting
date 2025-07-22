package dev.whysoezzy.domain.repository

import dev.whysoezzy.domain.models.Community

interface CommunitiesRepository {
    suspend fun getRecommendedCommunities(): Result<List<Community>>
    suspend fun getCommunityById(id: Long): Result<Community>
    suspend fun subscribeToCommunity(communityId: Long): Result<Unit>
    suspend fun unsubscribeFromCommunity(communityId: Long): Result<Unit>
    suspend fun searchCommunities(query: String): Result<List<Community>>
}
