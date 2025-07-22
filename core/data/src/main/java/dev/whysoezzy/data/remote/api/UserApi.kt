package dev.whysoezzy.data.remote.api

import dev.whysoezzy.data.remote.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

interface UserApi {
    @GET("user/profile")
    suspend fun getCurrentUser(): UserDto

    @PUT("user/profile")
    suspend fun updateUserProfile(@Body user: UserDto): UserDto

    @GET("user/{id}")
    suspend fun getUserById(@Path("id") id: Long): UserDto
}