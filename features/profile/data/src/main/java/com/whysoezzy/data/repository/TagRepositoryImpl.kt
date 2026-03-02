package com.whysoezzy.data.repository

import com.whysoezzy.data.api.TagsApiImpl
import com.whysoezzy.domain.models.Tag
import com.whysoezzy.domain.repository.TagRepository
import com.whysoezzy.network.safeApiCall

class TagRepositoryImpl(
    private val tagsApi: TagsApiImpl
) : TagRepository {

    override suspend fun getAllTags(): Result<List<Tag>> {
        return safeApiCall {
            val response = tagsApi.getAllTags()
            val tags = response.data ?: emptyList()
            tags.map { dto -> Tag(id = dto.id, name = dto.name) }
        }
    }
}
