package dev.whysoezzy.domain.models

data class MainScreenData(
    val heroEvents: List<Meeting>,
    val popularEvents: List<Meeting>,
    val recommendedCommunities: List<Community>,
    val allEvents: List<Meeting>,
    val adBlocks: List<AdBlock>,
)