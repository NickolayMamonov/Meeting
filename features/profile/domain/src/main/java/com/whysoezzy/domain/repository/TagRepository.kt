package com.whysoezzy.domain.repository

import com.whysoezzy.domain.models.Tag

interface TagRepository {
    suspend fun getAllTags(): Result<List<Tag>>
}
