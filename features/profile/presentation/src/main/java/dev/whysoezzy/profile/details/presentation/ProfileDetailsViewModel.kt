package dev.whysoezzy.profile.details.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.domain.models.CommunityInfo
import com.whysoezzy.domain.models.MeetingInfo
import com.whysoezzy.domain.models.MeetingStatus
import com.whysoezzy.domain.models.MeetingTag
import com.whysoezzy.domain.models.SocialMediaInfo
import com.whysoezzy.domain.models.SocialMediaType
import com.whysoezzy.domain.models.TagState
import com.whysoezzy.domain.usecase.GetCurrentUserUseCase
import com.whysoezzy.domain.usecase.GetUserByIdUseCase
import com.whysoezzy.domain.usecase.GetUserCommunitiesUseCase
import com.whysoezzy.domain.usecase.GetUserMeetingsUseCase
import dev.whysoezzy.profile.mappers.toUIKitCommunityInfoList
import dev.whysoezzy.profile.mappers.toUIKitMeetingInfo
import dev.whysoezzy.profile.mappers.toUIKitSocialMediaInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileDetailsViewModel(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getUserByIdUseCase: GetUserByIdUseCase,
    private val getUserMeetingsUseCase: GetUserMeetingsUseCase,
    private val getUserCommunitiesUseCase: GetUserCommunitiesUseCase
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
                val isOwnProfile = userId == null

                // Загружаем профиль пользователя
                val userResult = if (isOwnProfile) {
                    getCurrentUserUseCase()
                } else {
                    getUserByIdUseCase(userId)
                }

                userResult
                    .onSuccess { user ->
                        // Загружаем встречи и сообщества пользователя
                        val meetingsResult = getUserMeetingsUseCase(user.id)
                        val communitiesResult = getUserCommunitiesUseCase(user.id)

                        val meetings = meetingsResult.getOrNull() ?: emptyList()
                        val communities = communitiesResult.getOrNull() ?: emptyList()

                        _uiState.value = ProfileDetailsUiState.Success(
                            userId = user.id,
                            name = user.name,
                            surname = user.surname,
                            email = user.email,
                            city = user.city,
                            description = user.bio,
                            avatarUrl = user.avatar.takeIf { it.isNotBlank() },
                            interests = emptyList(), // TODO: добавить интересы из бэкенда
                            isOwnProfile = isOwnProfile,
                            socialMedias = user.socialMedias.map { it.toUIKitSocialMediaInfo() },
                            userMeetings = meetings.map { it.toUIKitMeetingInfo() },
                            userCommunities = communities.toUIKitCommunityInfoList(
                                subscribedIds = emptySet(), // TODO: получать подписки с бэкенда
                                onSubscribeClick = { communityId, isSubscribed ->
                                    handleToggleCommunitySubscription(communityId, isSubscribed)
                                },
                                onCardClick = { communityId ->
                                    handleNavigateToCommunity(communityId)
                                }
                            ),
                            subscribedCommunityIds = emptySet() // TODO: получать подписки с бэкенда
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

            // TODO: Добавить вызов API для синхронизации с сервером
        }
    }
}