package dev.whysoezzy.profile.details.presentation

sealed interface ProfileMode {
    data object Self : ProfileMode
    data class Other(val userId: Long) : ProfileMode
}