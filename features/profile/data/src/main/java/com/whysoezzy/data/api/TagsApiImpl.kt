package com.whysoezzy.data.api

import com.whysoezzy.data.dto.ApiResponse
import com.whysoezzy.data.dto.TagDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

class TagsApiImpl(private val client: HttpClient) {

    /**
     * GET /api/v1/tags — получить все доступные теги/интересы
     */
    suspend fun getAllTags(): ApiResponse<List<TagDto>> {
        return client.get("api/v1/tags").body()
    }
}
