package com.whysoezzy.data.api

import com.whysoezzy.data.dto.AdBlockResponseDto
import com.whysoezzy.data.dto.MeetingDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType

class MeetingsApiImpl(private val client: HttpClient) {
    suspend fun getHeroEvents(): List<MeetingDto> {
        return client.get("meetings/main").body()
    }

    suspend fun getPopularEvents(): List<MeetingDto> {
        return client.get("meetings/popular").body()
    }



    suspend fun getAllEvents(page: Int = 0, limit: Int = 20): List<MeetingDto> {
        return client.get("meetings") {
            parameter("page", page)
            parameter("limit", limit)
        }.body()
    }

    suspend fun searchEvents(query: String): List<MeetingDto> {
        return client.get("meetings/search") {
            parameter("query", query)
        }.body()
    }

    suspend fun getMeetingsById(id: Long): MeetingDto {
        return client.get("meetings/$id").body()
    }

    suspend fun joinMeeting(id: Long) {
        client.post("meetings/$id/join") {
            contentType(ContentType.Application.Json)
        }
    }

    suspend fun leaveMeeting(id: Long) {
        client.delete("meetings/$id/leave")
    }

    suspend fun getUserMeetings(): List<MeetingDto> {
        return client.get("user/meetings").body()
    }

    suspend fun getEventsByCategory(categoryId: Long): List<MeetingDto> {
        return client.get("meetings/category/$categoryId").body()
    }

    suspend fun getEventsByCommunity(communityId: Long): List<MeetingDto> {
        return client.get("communities/$communityId/meetings").body()
    }

    suspend fun getAdBlocks(): List<AdBlockResponseDto> {
        return client.get("/api/ads").body()
    }

}