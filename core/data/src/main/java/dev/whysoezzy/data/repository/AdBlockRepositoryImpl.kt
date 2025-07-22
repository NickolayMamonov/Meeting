package dev.whysoezzy.data.repository

import dev.whysoezzy.domain.models.AdBlock
import dev.whysoezzy.domain.repository.AdBlockRepository

class AdBlockRepositoryImpl : AdBlockRepository {

    override suspend fun getAdBlocks(): Result<List<AdBlock>> {
        // TODO: Implement API call
        return Result.success(emptyList())
    }
}