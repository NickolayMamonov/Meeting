package dev.whysoezzy.data.remote.api

import dev.whysoezzy.data.remote.dto.CommunityDto
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CommunitiesApi {
    @GET("communities/recommended")
    suspend fun getRecommendedCommunities(): List<CommunityDto>

    @GET("communities/{id}")
    suspend fun getCommunityById(@Path("id") id: Long): CommunityDto

    @POST("communities/{id}/subscribe")
    suspend fun subscribeToCommunity(@Path("id") id: Long)

    @DELETE("communities/{id}/subscribe")
    suspend fun unsubscribeFromCommunity(@Path("id") id: Long)

    @GET("communities/search")
    suspend fun searchCommunities(@Query("query") query: String): List<CommunityDto>
}