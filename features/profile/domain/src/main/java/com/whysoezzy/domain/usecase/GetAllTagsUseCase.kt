package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.models.Tag
import com.whysoezzy.domain.repository.TagRepository

class GetAllTagsUseCase(private val tagRepository: TagRepository) {
    suspend operator fun invoke(): Result<List<Tag>> = tagRepository.getAllTags()
}
