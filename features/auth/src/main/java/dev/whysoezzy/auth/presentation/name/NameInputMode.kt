package dev.whysoezzy.auth.presentation.name

sealed interface NameInputMode {
    data object Onboarding : NameInputMode

    data object ProfileCompletion : NameInputMode
}
