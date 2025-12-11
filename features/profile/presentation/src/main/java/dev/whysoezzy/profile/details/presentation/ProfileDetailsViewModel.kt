package dev.whysoezzy.profile.details.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.whysoezzy.domain.models.CommunityInfo
import dev.whysoezzy.domain.models.MeetingInfo
import dev.whysoezzy.domain.models.MeetingStatus
import dev.whysoezzy.domain.models.MeetingTag
import dev.whysoezzy.domain.models.SocialMediaInfo
import dev.whysoezzy.domain.models.SocialMediaType
import dev.whysoezzy.domain.models.TagState
import dev.whysoezzy.profile.domain.usecase.GetUserProfileUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileDetailsViewModel(
    private val getUserProfileUseCase: GetUserProfileUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileDetailsUiState>(ProfileDetailsUiState.Loading)
    val uiState: StateFlow<ProfileDetailsUiState> = _uiState.asStateFlow()

    fun onEvent(event: ProfileDetailsEvent) {
        when (event) {
            is ProfileDetailsEvent.LoadProfile -> loadProfile(event.userId)
            is ProfileDetailsEvent.EditProfile -> handleEditProfile()
            is ProfileDetailsEvent.ShareProfile -> handleShareProfile()
            is ProfileDetailsEvent.NavigateToMeeting -> handleNavigateToMeeting(event.meetingId)
            is ProfileDetailsEvent.NavigateToCommunity -> handleNavigateToCommunity(event.communityId)
            is ProfileDetailsEvent.OpenSocialMedia -> handleOpenSocialMedia(event.url)
            is ProfileDetailsEvent.ToggleCommunitySubscription -> handleToggleCommunitySubscription(
                event.communityId,
                event.isSubscribed
            )
        }
    }

    private fun loadProfile(userId: Long?) {
        viewModelScope.launch {
            _uiState.value = ProfileDetailsUiState.Loading

            try {
                getUserProfileUseCase()
                    .onSuccess { user ->
                        // Временные mock данные для демонстрации
                        val mockSocialMedias = listOf(
                            SocialMediaInfo(
                                type = SocialMediaType.TELEGRAM,
                                url = "https://t.me/username",
                                username = "@username"
                            ),
                            SocialMediaInfo(
                                type = SocialMediaType.HABR,
                                url = "https://habr.com/users/username",
                                username = "username"
                            )
                        )

                        val mockTags = listOf(
                            MeetingTag(1, "Android", TagState.ACTIVE),
                            MeetingTag(2, "Kotlin", TagState.ACTIVE)
                        )

                        val mockMeetings = listOf(
                            MeetingInfo(
                                id = 1,
                                imageUrl = "https://picsum.photos/212/148?random=1",
                                title = "Android Dev Meetup",
                                address = "ул. Тверская, 15",
                                tags = mockTags,
                                time = System.currentTimeMillis(),
                                meetingStatus = MeetingStatus.ACTIVE
                            ),
                            MeetingInfo(
                                id = 2,
                                imageUrl = "https://picsum.photos/212/148?random=2",
                                title = "Kotlin Conf",
                                address = "ул. Пушкина, 10",
                                tags = mockTags.take(1),
                                time = System.currentTimeMillis() + 86400000,
                                meetingStatus = MeetingStatus.ACTIVE
                            )
                        )

                        val mockCommunities = listOf(
                            CommunityInfo(
                                id = 1,
                                title = "Android Developers Moscow",
                                description = "Сообщество разработчиков Android в Москве",
                                imageUrl = "https://picsum.photos/300/300?random=1",
                                membersCount = 1250
                            ),
                            CommunityInfo(
                                id = 2,
                                title = "Kotlin User Group",
                                description = "Kotlin enthusiasts",
                                imageUrl = "https://picsum.photos/300/300?random=2",
                                membersCount = 890
                            )
                        )

                        _uiState.value = ProfileDetailsUiState.Success(
                            userId = user.id,
                            name = user.name,
                            surname = user.surname,
                            email = user.email,
                            description = user.description.ifBlank {
                                "Senior Android Developer в крутой компании. Люблю изучать новые технологии и делиться знаниями с сообществом."
                            },
                            avatarUrl = user.imageUrl,
                            isOwnProfile = userId == null, // null означает собственный профиль
                            socialMedias = mockSocialMedias,
                            userMeetings = mockMeetings,
                            userCommunities = mockCommunities,
                            subscribedCommunityIds = setOf(1, 2) // Mock данные подписок
                        )
                    }
                    .onFailure { exception ->
                        _uiState.value = ProfileDetailsUiState.Error(
                            message = exception.message ?: "Не удалось загрузить профиль"
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = ProfileDetailsUiState.Error(
                    message = e.message ?: "Произошла ошибка"
                )
            }
        }
    }

    private fun handleEditProfile() {
        // Логика перехода к редактированию профиля
        // Обычно здесь вызывается навигация через Navigator или SharedFlow с событием
    }

    private fun handleShareProfile() {
        // Логика поделиться профилем
        // Здесь можно вызвать системный шеринг или скопировать ссылку в буфер
    }

    private fun handleNavigateToMeeting(meetingId: Long) {
        // Логика навигации к встрече
    }

    private fun handleNavigateToCommunity(communityId: Long) {
        // Логика навигации к сообществу
    }

    private fun handleOpenSocialMedia(url: String) {
        // Логика открытия социальной сети (обычно через браузер)
    }

    private fun handleToggleCommunitySubscription(communityId: Long, isSubscribed: Boolean) {
        // Логика изменения подписки на сообщество
        val currentState = _uiState.value
        if (currentState is ProfileDetailsUiState.Success) {
            val updatedSubscriptions = if (isSubscribed) {
                currentState.subscribedCommunityIds + communityId
            } else {
                currentState.subscribedCommunityIds - communityId
            }

            _uiState.value = currentState.copy(
                subscribedCommunityIds = updatedSubscriptions
            )

            // Здесь можно добавить вызов API для синхронизации с сервером
        }
    }
}
