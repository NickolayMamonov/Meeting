package dev.whysoezzy.communities.details.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.whysoezzy.domain.models.MeetingInfo
import dev.whysoezzy.domain.models.MeetingStatus
import dev.whysoezzy.domain.models.MeetingTag
import dev.whysoezzy.domain.models.Person
import dev.whysoezzy.domain.models.TagState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CommunityDetailsViewModel : ViewModel() {

    private val _uiState =
        MutableStateFlow<CommunityDetailsUiState>(CommunityDetailsUiState.Loading)
    val uiState: StateFlow<CommunityDetailsUiState> = _uiState.asStateFlow()

    fun onEvent(event: CommunityDetailsEvent) {
        when (event) {
            is CommunityDetailsEvent.LoadCommunity -> loadCommunityDetails(event.communityId)
            CommunityDetailsEvent.ToggleSubscription -> toggleSubscription()
            is CommunityDetailsEvent.NavigateToMeeting -> {
                // Навигация к встрече
            }

            is CommunityDetailsEvent.NavigateToProfile -> {
                // Навигация к профилю
            }

            CommunityDetailsEvent.NavigateToSubscribers -> {
                // Навигация к списку подписчиков
            }
        }
    }

    private fun loadCommunityDetails(communityId: Long) {
        viewModelScope.launch {
            _uiState.value = CommunityDetailsUiState.Loading

            try {
                // Mock data
                val mockTags = listOf(
                    MeetingTag(1, "Android", TagState.ACTIVE),
                    MeetingTag(2, "Kotlin", TagState.ACTIVE),
                    MeetingTag(3, "Mobile", TagState.ACTIVE),
                    MeetingTag(4, "Development", TagState.ACTIVE)
                )

                val mockSubscribers = listOf(
                    Person(
                        1,
                        "Анна",
                        "Иванова",
                        "https://picsum.photos/100/100?random=1",
                        "Дизайнер"
                    ),
                    Person(
                        2,
                        "Петр",
                        "Петров",
                        "https://picsum.photos/100/100?random=2",
                        "Android Dev"
                    ),
                    Person(3, "Мария", "Сидорова", "https://picsum.photos/100/100?random=3", "PM"),
                    Person(4, "Алексей", "Козлов", "https://picsum.photos/100/100?random=4", "QA"),
                    Person(
                        5,
                        "Ольга",
                        "Новикова",
                        "https://picsum.photos/100/100?random=5",
                        "Backend"
                    )
                )

                val mockActiveMeetings = listOf(
                    MeetingInfo(
                        id = 1,
                        imageUrl = "https://picsum.photos/320/180?random=meeting1",
                        title = "Мастер-класс по Jetpack Compose",
                        address = "ул. Пушкина, 10",
                        tags = mockTags.take(2),
                        time = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000,
                        meetingStatus = MeetingStatus.ACTIVE
                    ),
                    MeetingInfo(
                        id = 2,
                        imageUrl = "https://picsum.photos/320/180?random=meeting2",
                        title = "Конференция Android DevFest",
                        address = "ул. Ленина, 5",
                        tags = mockTags.take(3),
                        time = System.currentTimeMillis() + 14 * 24 * 60 * 60 * 1000,
                        meetingStatus = MeetingStatus.ACTIVE
                    ),
                    MeetingInfo(
                        id = 3,
                        imageUrl = "https://picsum.photos/320/180?random=meeting3",
                        title = "Обзор новостей Android 15",
                        address = "Коворкинг TechSpace",
                        tags = mockTags.take(2),
                        time = System.currentTimeMillis() + 21 * 24 * 60 * 60 * 1000,
                        meetingStatus = MeetingStatus.ACTIVE
                    )
                )

                val mockPastMeetings = listOf(
                    MeetingInfo(
                        id = 4,
                        imageUrl = "https://picsum.photos/212/148?random=past1",
                        title = "Kotlin Multiplatform в продакшне",
                        address = "ст. м. Охотный ряд",
                        tags = mockTags.drop(1).take(2),
                        time = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000,
                        meetingStatus = MeetingStatus.COMPLETED
                    ),
                    MeetingInfo(
                        id = 5,
                        imageUrl = "https://picsum.photos/212/148?random=past2",
                        title = "Оптимизация производительности",
                        address = "онлайн",
                        tags = mockTags.take(2),
                        time = System.currentTimeMillis() - 14 * 24 * 60 * 60 * 1000,
                        meetingStatus = MeetingStatus.COMPLETED
                    ),
                    MeetingInfo(
                        id = 6,
                        imageUrl = "https://picsum.photos/212/148?random=past3",
                        title = "Clean Architecture в Android",
                        address = "Офис Google",
                        tags = mockTags.drop(2),
                        time = System.currentTimeMillis() - 21 * 24 * 60 * 60 * 1000,
                        meetingStatus = MeetingStatus.COMPLETED
                    )
                )

                _uiState.value = CommunityDetailsUiState.Success(
                    communityId = communityId,
                    imageUrl = "https://picsum.photos/800/400?random=community$communityId",
                    title = "Android Developers Moscow",
                    tags = mockTags,
                    description = "Крупнейшее сообщество разработчиков Android в Москве. Мы организуем регулярные встречи, мастер-классы, конференции и хакатоны. Присоединяйтесь к нам, чтобы совершенствовать свои навыки и находить единомышленников!",
                    isSubscribed = false,
                    subscribers = mockSubscribers,
                    activeMeetings = mockActiveMeetings,
                    pastMeetings = mockPastMeetings
                )
            } catch (e: Exception) {
                _uiState.value = CommunityDetailsUiState.Error(
                    message = e.message ?: "Не удалось загрузить информацию о сообществе"
                )
            }
        }
    }

    private fun toggleSubscription() {
        val currentState = _uiState.value
        if (currentState is CommunityDetailsUiState.Success) {
            _uiState.value = currentState.copy(
                isSubscribed = !currentState.isSubscribed
            )
        }
    }
}