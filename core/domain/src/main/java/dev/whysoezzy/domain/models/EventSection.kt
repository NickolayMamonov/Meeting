package dev.whysoezzy.domain.models

data class EventSection(
    val title: String,
    val events: List<Meeting>,
    val displayType: EventDisplayType,
    val isHorizontal: Boolean
)