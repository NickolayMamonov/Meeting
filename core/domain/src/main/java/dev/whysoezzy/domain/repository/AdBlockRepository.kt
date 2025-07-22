package dev.whysoezzy.domain.repository

import dev.whysoezzy.domain.models.AdBlock

interface AdBlockRepository {
    suspend fun getAdBlocks(): Result<List<AdBlock>>
}