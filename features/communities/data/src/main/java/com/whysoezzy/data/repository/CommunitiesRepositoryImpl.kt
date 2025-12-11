package com.whysoezzy.data.repository

import com.whysoezzy.data.api.CommunitiesApiImpl
import com.whysoezzy.data.mapper.CommunityMapper
import com.whysoezzy.domain.models.Community
import com.whysoezzy.domain.repository.CommunitiesRepository
import com.whysoezzy.network.safeApiCall

class CommunitiesRepositoryImpl(
    private val communitiesApi: CommunitiesApiImpl,
    private val communityMapper: CommunityMapper
): CommunitiesRepository {
    override suspend fun getRecommendedCommunities(): Result<List<Community>> {
        return safeApiCall {
            val response = communitiesApi.getRecommendedCommunities()
            response.map { communityMapper.toDomain(it) }
        }
    }

    override suspend fun getCommunityById(id: Long): Result<Community> {
        return safeApiCall {
            val response = communitiesApi.getCommunityById(id)
            communityMapper.toDomain(response)
        }
    }

    override suspend fun subscribeToCommunity(id: Long): Result<Unit> {
        return safeApiCall {
            communitiesApi.subscribeToCommunity(id)
        }
    }

    override suspend fun unsubscribeFromCommunity(id: Long): Result<Unit> {
        return safeApiCall {
            communitiesApi.unsubscribeFromCommunity(id)
        }
    }

    override suspend fun searchCommunities(query: String): Result<List<Community>> {
        return safeApiCall {
            val response = communitiesApi.searchCommunities(query)
            response.map { communityMapper.toDomain(it) }
        }
    }
}