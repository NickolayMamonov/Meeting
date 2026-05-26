package com.whysoezzy.data.api

import com.whysoezzy.data.dto.ApiResponse
import com.whysoezzy.data.dto.TagDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

internal class TagsApiKtor(private val client: HttpClient): TagsApi {

    /**
     * GET /api/v1/tags — получить все доступные теги/интересы
     */
    override suspend fun getAllTags(): ApiResponse<List<TagDto>> {
        return client.get("api/v1/tags").body()
    }
}
