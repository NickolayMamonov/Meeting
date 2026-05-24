package com.whysoezzy.data.api

import com.whysoezzy.data.dto.CommunityDto
import com.whysoezzy.data.dto.MeetingDto
import com.whysoezzy.data.dto.UserInfoDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType

class CommunitiesApiKtor(private val client: HttpClient): CommunitiesApi {
    override suspend fun getRecommendedCommunities(): List<CommunityDto> {
        return client.get("communities/recommended").body()
    }

    override suspend fun getCommunityById(id: Long): CommunityDto {
        return client.get("communities/$id").body()
    }

    override suspend fun subscribeToCommunity(id: Long) {
        client.post("communities/$id/subscribe") {
            contentType(ContentType.Application.Json)
        }
    }

    override suspend fun unsubscribeFromCommunity(id: Long) {
        client.delete("communities/$id/subscribe")
    }

    override suspend fun searchCommunities(query: String): List<CommunityDto> {
        return client.get("communities/search") {
            parameter("query", query)
        }.body()
    }

    override suspend fun getCommunityMeetings(id: Long): List<MeetingDto> {
        return client.get("communities/$id/meetings").body()
    }

    override suspend fun getCommunitySubscribers(id: Long): List<UserInfoDto> {
        return client.get("communities/$id/subscribers").body()
    }
}