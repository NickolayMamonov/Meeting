package dev.whysoezzy.data.remote.api

import dev.whysoezzy.data.remote.dto.MeetingDto
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MeetingsApi {
    @GET("meetings/hero")
    suspend fun getHeroEvents(): List<MeetingDto>

    @GET("meetings/popular")
    suspend fun getPopularEvents(): List<MeetingDto>

    @GET("meetings")
    suspend fun getAllEvents(
        @Query("page") page: Int = 0,
        @Query("limit") limit: Int = 20
    ): List<MeetingDto>

    @GET("meetings/search")
    suspend fun searchEvents(@Query("query") query: String): List<MeetingDto>

    @GET("meetings/{id}")
    suspend fun getMeetingById(@Path("id") id: Long): MeetingDto

    @POST("meetings/{id}/join")
    suspend fun joinMeeting(@Path("id") id: Long)

    @DELETE("meetings/{id}/join")
    suspend fun leaveMeeting(@Path("id") id: Long)

    @GET("user/meetings")
    suspend fun getUserMeetings(): List<MeetingDto>

    @GET("meetings/category/{categoryId}")
    suspend fun getEventsByCategory(@Path("categoryId") categoryId: Long): List<MeetingDto>

    @GET("communities/{communityId}/meetings")
    suspend fun getEventsByCommunity(@Path("communityId") communityId: Long): List<MeetingDto>
}