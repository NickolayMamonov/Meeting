package dev.whysoezzy.data.repository

import dev.whysoezzy.domain.models.Community
import dev.whysoezzy.domain.repository.CommunitiesRepository

class CommunitiesRepositoryImpl : CommunitiesRepository {

    override suspend fun getRecommendedCommunities(): Result<List<Community>> {
        // TODO: Implement API call
        return Result.success(emptyList())
    }

    override suspend fun getCommunityById(id: Long): Result<Community> {
        TODO("Not yet implemented")
    }

    override suspend fun searchCommunities(query: String): Result<List<Community>> {
        // TODO: Implement API call
        return Result.success(emptyList())
    }

    override suspend fun subscribeToCommunity(communityId: Long): Result<Unit> {
        // TODO: Implement API call
        return Result.success(Unit)
    }

    override suspend fun unsubscribeFromCommunity(communityId: Long): Result<Unit> {
        // TODO: Implement API call
        return Result.success(Unit)
    }
}