package dev.whysoezzy.domain.models

enum class MeetingStatus {
    ACTIVE,      // Активное событие, можно регистрироваться
    COMPLETED,   // Событие завершилось
    CANCELLED,   // Событие отменено
    FULL,        // Событие заполнено (нет мест)
    DRAFT        // Черновик события
}