package com.whysoezzy.data.api

import com.whysoezzy.data.dto.AdBlockResponseDto
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

class MeetingsApiImpl(private val client: HttpClient) {

    /** GET /meetings/main — главный экран */
    suspend fun getHeroEvents(): List<MeetingDto> {
        return client.get("meetings/main").body()
    }

    /** GET /meetings/popular — популярные */
    suspend fun getPopularEvents(): List<MeetingDto> {
        return client.get("meetings/popular").body()
    }

    /** GET /meetings?page=&limit=&tagId= — все встречи с фильтром по тегу */
    suspend fun getAllEvents(page: Int = 0, limit: Int = 20, tagId: Long? = null): List<MeetingDto> {
        return client.get("meetings") {
            parameter("page", page)
            parameter("limit", limit)
            if (tagId != null) parameter("tagId", tagId)
        }.body()
    }

    /** GET /meetings/search?query= */
    suspend fun searchEvents(query: String): List<MeetingDto> {
        return client.get("meetings/search") {
            parameter("query", query)
        }.body()
    }

    /** GET /meetings/{id} */
    suspend fun getMeetingsById(id: Long): MeetingDto {
        return client.get("meetings/$id").body()
    }

    /** GET /meetings/{id}/participants — экран People */
    suspend fun getMeetingParticipants(id: Long): List<UserInfoDto> {
        return client.get("meetings/$id/participants").body()
    }

    /** POST /meetings/{id}/join */
    suspend fun joinMeeting(id: Long) {
        client.post("meetings/$id/join") {
            contentType(ContentType.Application.Json)
        }
    }

    /** DELETE /meetings/{id}/leave */
    suspend fun leaveMeeting(id: Long) {
        client.delete("meetings/$id/leave")
    }

    /** GET /user/meetings */
    suspend fun getUserMeetings(): List<MeetingDto> {
        return client.get("user/meetings").body()
    }

    /** GET /api/ads */
    suspend fun getAdBlocks(): List<AdBlockResponseDto> {
        return client.get("/api/ads").body()
    }
}
