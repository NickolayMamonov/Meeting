package com.whysoezzy.data.api

import com.whysoezzy.data.dto.ApiResponse
import com.whysoezzy.data.dto.TagDto

interface TagsApi{
    suspend fun getAllTags(): ApiResponse<List<TagDto>>
}