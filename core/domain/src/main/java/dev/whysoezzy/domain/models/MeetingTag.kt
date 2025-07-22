package dev.whysoezzy.domain.models

data class MeetingTag(
    val id: Long,
    val text: String,
    val state: TagState
)

enum class TagState {
    ACTIVE,       // Активный тег (обычное состояние)
    INACTIVE,     // Неактивный тег (серый)
    SELECTED,     // Выбранный тег (для фильтрации)
    DISABLED      // Отключенный тег (нельзя взаимодействовать)
}
