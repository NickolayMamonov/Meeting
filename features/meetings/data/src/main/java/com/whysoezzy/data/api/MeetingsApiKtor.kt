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

internal class MeetingsApiKtor(
    private val client: HttpClient,
) : MeetingsApi {
    /** GET /meetings/main — главный экран */
    override suspend fun getHeroEvents(): List<MeetingDto> {
        return client.get("meetings/main").body()
    }

    /** GET /meetings/popular — популярные */
    override suspend fun getPopularEvents(): List<MeetingDto> {
        return client.get("meetings/popular").body()
    }

    /** GET /meetings?page=&limit=&tagId= — все встречи с фильтром по тегу */
    override suspend fun getAllEvents(page: Int, limit: Int, tagId: Long?): List<MeetingDto> {
        return client
            .get("meetings") {
                parameter("page", page)
                parameter("limit", limit)
                if (tagId != null) parameter("tagId", tagId)
            }.body()
    }

    /** GET /meetings/search?query= */
    override suspend fun searchEvents(query: String): List<MeetingDto> {
        return client
            .get("meetings/search") {
                parameter("query", query)
            }.body()
    }

    /** GET /meetings/{id} */
    override suspend fun getMeetingsById(id: Long): MeetingDto {
        return client.get("meetings/$id").body()
    }

    /** GET /meetings/{id}/participants — экран People */
    override suspend fun getMeetingParticipants(id: Long): List<UserInfoDto> {
        return client.get("meetings/$id/participants").body()
    }

    /** POST /meetings/{id}/join */
    override suspend fun joinMeeting(id: Long) {
        client.post("meetings/$id/join") {
            contentType(ContentType.Application.Json)
        }
    }

    /** DELETE /meetings/{id}/leave */
    override suspend fun leaveMeeting(id: Long) {
        client.delete("meetings/$id/leave")
    }

    /** GET /user/meetings */
    override suspend fun getUserMeetings(): List<MeetingDto> {
        return client.get("user/meetings").body()
    }

    /** GET /api/ads */
    override suspend fun getAdBlocks(): List<AdBlockResponseDto> {
        return client.get("/api/ads").body()
    }
}
