package com.whysoezzy.data.api

import com.whysoezzy.data.dto.UserDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class UserApiImpl(private val client: HttpClient) {
    suspend fun getCurrentUser(): UserDto {
        return client.get("users/profile").body()
    }

    suspend fun updateUserProfile(user: UserDto): UserDto {
        return client.put("users/profile") {
            contentType(ContentType.Application.Json)
            setBody(user)
        }.body()
    }

    suspend fun getUserById(id: Long): UserDto {
        return client.get("users/$id").body()
    }
}