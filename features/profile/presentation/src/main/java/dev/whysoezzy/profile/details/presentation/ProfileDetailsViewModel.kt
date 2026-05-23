package dev.whysoezzy.profile.details.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.auth.domain.usecase.LogoutUseCase
import com.whysoezzy.network.error.ApiException
import com.whysoezzy.domain.usecase.GetCurrentUserUseCase
import com.whysoezzy.domain.usecase.GetUserByIdUseCase
import com.whysoezzy.domain.usecase.GetUserCommunitiesUseCase
import com.whysoezzy.domain.usecase.GetUserMeetingsUseCase
import com.whysoezzy.domain.usecase.ManageCommunitySubscriptionUseCase
import com.whysoezzy.network.toUserMessage
import dev.whysoezzy.profile.mappers.toUIKitCommunityInfoList
import dev.whysoezzy.profile.mappers.toUIKitMeetingInfo
import dev.whysoezzy.profile.mappers.toUIKitSocialMediaInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileDetailsViewModel(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getUserByIdUseCase: GetUserByIdUseCase,
    private val getUserMeetingsUseCase: GetUserMeetingsUseCase,
    private val getUserCommunitiesUseCase: GetUserCommunitiesUseCase,
    private val manageCommunitySubscriptionUseCase: ManageCommunitySubscriptionUseCase,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileDetailsUiState>(ProfileDetailsUiState.Loading)
    val uiState: StateFlow<ProfileDetailsUiState> = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<ProfileDetailsNavEvent>(extraBufferCapacity = 1)
    val navEvent: SharedFlow<ProfileDetailsNavEvent> = _navEvent.asSharedFlow()

    fun onEvent(event: ProfileDetailsEvent) {
        when (event) {
            is ProfileDetailsEvent.LoadProfile -> loadProfile(event.userId)
            is ProfileDetailsEvent.EditProfile -> viewModelScope.launch {
                _navEvent.emit(ProfileDetailsNavEvent.NavigateToEdit)
            }
            is ProfileDetailsEvent.ShareProfile -> handleShareProfile()
            is ProfileDetailsEvent.Logout -> handleLogout()
            is ProfileDetailsEvent.NavigateToMeeting -> viewModelScope.launch {
                _navEvent.emit(ProfileDetailsNavEvent.NavigateToMeeting(event.meetingId))
            }
            is ProfileDetailsEvent.NavigateToCommunity -> viewModelScope.launch {
                _navEvent.emit(ProfileDetailsNavEvent.NavigateToCommunity(event.communityId))
            }
            is ProfileDetailsEvent.OpenSocialMedia -> handleOpenSocialMedia(event.url)
            is ProfileDetailsEvent.ToggleCommunitySubscription ->
                handleToggleCommunitySubscription(event.communityId, event.isSubscribed)
        }
    }

    private fun loadProfile(userId: Long?) {
        viewModelScope.launch {
            _uiState.value = ProfileDetailsUiState.Loading

            val isOwnProfile = userId == null
            val userResult = if (isOwnProfile) getCurrentUserUseCase() else getUserByIdUseCase(userId)

            userResult
                .onFailure { exception ->
                    if (exception is ApiException.UnauthorizedError) {
                        handleLogout()
                    } else {
                        _uiState.value = ProfileDetailsUiState.Error(
                            message = exception.toUserMessage()
                        )
                    }
                }
                .onSuccess { user ->
                    if (isOwnProfile && user.name.isBlank()) {
                        _navEvent.emit(ProfileDetailsNavEvent.NavigateToNameInput)
                        return@onSuccess
                    }

                    val meetingsDeferred    = async { getUserMeetingsUseCase(user.id) }
                    val communitiesDeferred = async { getUserCommunitiesUseCase(user.id) }

                    val meetings = meetingsDeferred.await().getOrNull() ?: emptyList()
                    val communities = communitiesDeferred.await().getOrNull() ?: emptyList()

                    val subscribedIds = communities.map { it.id }.toSet()

                    _uiState.value = ProfileDetailsUiState.Success(
                        userId = user.id,
                        name = user.name,
                        surname = user.surname,
                        email = user.email,
                        city = user.city,
                        description = user.bio,
                        avatarUrl = user.avatar.takeIf { it.isNotBlank() },
                        interests = user.interests.map { it.name },
                        isOwnProfile = isOwnProfile,
                        socialMedias = user.socialMedias.map { it.toUIKitSocialMediaInfo() },
                        userMeetings = meetings.map { it.toUIKitMeetingInfo() },
                        userCommunities = communities.toUIKitCommunityInfoList(
                            subscribedIds = subscribedIds
                        ),
                    )
                }
        }
    }

    private fun handleLogout() {
        viewModelScope.launch {
            logoutUseCase()
            _navEvent.emit(ProfileDetailsNavEvent.NavigateToAuth)
        }
    }

    private fun handleShareProfile() {
        val state = _uiState.value as? ProfileDetailsUiState.Success ?: return
        viewModelScope.launch {
            _navEvent.emit(
                ProfileDetailsNavEvent.ShareProfile(
                    name = "${state.name} ${state.surname}".trim(),
                    shareText = "Посмотри профиль ${state.name} ${state.surname} в приложении Meeting!"
                )
            )
        }
    }

    private fun handleOpenSocialMedia(url: String) {
        viewModelScope.launch {
            _navEvent.emit(ProfileDetailsNavEvent.OpenSocialMedia(url))
        }
    }

    private fun handleToggleCommunitySubscription(communityId: Long, isSubscribed: Boolean) {
        applySubscriptionToState(communityId, isSubscribed)

        viewModelScope.launch {
            manageCommunitySubscriptionUseCase(communityId, isSubscribed)
                .onFailure {
                    applySubscriptionToState(communityId, !isSubscribed)
                }
        }
    }

    private fun applySubscriptionToState(communityId: Long, isSubscribed: Boolean) {
        val state = _uiState.value as? ProfileDetailsUiState.Success ?: return
        _uiState.value = state.copy(
            userCommunities = state.userCommunities.map { community ->
                if (community.id == communityId) community.copy(isSubscribed = isSubscribed)
                else community
            }
        )
    }
}
