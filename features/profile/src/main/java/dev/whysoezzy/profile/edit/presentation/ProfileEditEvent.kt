package dev.whysoezzy.profile.edit.presentation

sealed class ProfileEditEvent {
    data class UpdateName(val name: String) : ProfileEditEvent()
    data class UpdateSurname(val surname: String) : ProfileEditEvent()
    data class UpdateEmail(val email: String) : ProfileEditEvent()
    data class UpdateCity(val city: String) : ProfileEditEvent()
    data class UpdateDescription(val description: String) : ProfileEditEvent()
    object Save : ProfileEditEvent()
    object LoadProfile : ProfileEditEvent()
}