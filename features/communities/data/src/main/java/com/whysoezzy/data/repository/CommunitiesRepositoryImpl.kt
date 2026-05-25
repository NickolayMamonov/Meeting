package com.whysoezzy.data.repository

import com.whysoezzy.data.api.CommunitiesApi
import com.whysoezzy.data.mapper.toDomain
import com.whysoezzy.data.mapper.toPerson
import com.whysoezzy.domain.models.Community
import com.whysoezzy.domain.models.Meeting
import com.whysoezzy.domain.models.Person
import com.whysoezzy.domain.repository.CommunitiesRepository
import com.whysoezzy.network.safeApiCall

class CommunitiesRepositoryImpl(
    private val communitiesApi: CommunitiesApi
) : CommunitiesRepository {

    override suspend fun getRecommendedCommunities(): Result<List<Community>> = safeApiCall {
        communitiesApi.getRecommendedCommunities().map { it.toDomain() }
    }

    override suspend fun getCommunityById(id: Long): Result<Community> = safeApiCall {
        communitiesApi.getCommunityById(id).toDomain()
    }

    override suspend fun subscribeToCommunity(id: Long): Result<Unit> = safeApiCall {
        communitiesApi.subscribeToCommunity(id)
    }

    override suspend fun unsubscribeFromCommunity(id: Long): Result<Unit> = safeApiCall {
        communitiesApi.unsubscribeFromCommunity(id)
    }

    override suspend fun searchCommunities(query: String): Result<List<Community>> = safeApiCall {
        communitiesApi.searchCommunities(query).map { it.toDomain() }
    }

    override suspend fun getCommunityMeetings(id: Long): Result<List<Meeting>> = safeApiCall {
        communitiesApi.getCommunityMeetings(id).map { it.toDomain() }
    }

    override suspend fun getCommunitySubscribers(id: Long): Result<List<Person>> = safeApiCall {
        communitiesApi.getCommunitySubscribers(id).map { it.toPerson() }
    }
}