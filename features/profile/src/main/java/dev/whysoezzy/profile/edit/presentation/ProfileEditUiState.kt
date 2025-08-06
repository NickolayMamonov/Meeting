package dev.whysoezzy.profile.edit.presentation

import dev.whysoezzy.domain.models.MeetingTag
import dev.whysoezzy.domain.models.SocialMedia

data class ProfileEditUiState(
    val name: String = "",
    val surname: String = "",
    val city: String = "",
    val description: String = "",
    val email: String = "",
    val imageUrl: String = "",
    val interests: List<MeetingTag> = emptyList(),
    val socialMedias: Map<SocialMedia, String> = emptyMap(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val nameError: String? = null,
    val surnameError: String? = null,
    val emailError: String? = null,
    val descriptionError: String? = null,
    val error: String? = null
) {
    val isValid: Boolean = nameError == null && surnameError == null && emailError == null &&
            name.isNotBlank() && surname.isNotBlank()
}