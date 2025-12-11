package com.whysoezzy.data.api

import com.whysoezzy.data.dto.CommunityDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType

class CommunitiesApiImpl(private val client: HttpClient) {
    suspend fun getRecommendedCommunities(): List<CommunityDto> {
        return client.get("communities/recommended").body()
    }

    suspend fun getCommunityById(id: Long): CommunityDto {
        return client.get("communities/$id").body()
    }

    suspend fun subscribeToCommunity(id: Long) {
        client.post("communities/$id/subscribe") {
            contentType(ContentType.Application.Json)
        }
    }

    suspend fun unsubscribeFromCommunity(id: Long) {
        client.delete("communities/$id/subscribe")
    }

    suspend fun searchCommunities(query: String): List<CommunityDto> {
        return client.get("communities/search") {
            parameter("query", query)
        }.body()
    }
}