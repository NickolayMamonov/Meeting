package com.whysoezzy.data.repository

import com.whysoezzy.data.api.TagsApi
import com.whysoezzy.domain.models.Tag
import com.whysoezzy.domain.repository.TagRepository
import com.whysoezzy.network.safeApiCall

internal class TagRepositoryImpl(
    private val tagsApi: TagsApi
) : TagRepository {

    override suspend fun getAllTags(): Result<List<Tag>> {
        return safeApiCall {
            val response = tagsApi.getAllTags()
            val tags = response.data ?: emptyList()
            tags.map { dto -> Tag(id = dto.id, name = dto.name) }
        }
    }
}
