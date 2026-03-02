package com.whysoezzy.data.api

import com.whysoezzy.data.dto.CommunityInfoDto
import com.whysoezzy.data.dto.MeetingInfoDto
import com.whysoezzy.data.dto.UpdateUserDto
import com.whysoezzy.data.dto.UserProfileDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class UserApiImpl(private val client: HttpClient) {
    suspend fun getCurrentUserProfile(): UserProfileDto {
        return client.get("profile").body()
    }

    suspend fun getUserProfile(id: Long): UserProfileDto {
        return client.get("users/$id").body()
    }

    suspend fun updateUserProfile(updateDto: UpdateUserDto): UserProfileDto {
        return client.put("profile") {
            contentType(ContentType.Application.Json)
            setBody(updateDto)
        }.body()
    }

    suspend fun getUserMeetings(userId: Long): List<MeetingInfoDto> {
        return client.get("users/$userId/meetings").body()
    }

    suspend fun getUserCommunities(userId: Long): List<CommunityInfoDto> {
        return client.get("users/$userId/communities").body()
    }
}