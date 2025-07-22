package dev.whysoezzy.domain.models

data class SearchResults(
    val events: List<Meeting>,
    val communities: List<Community>
)