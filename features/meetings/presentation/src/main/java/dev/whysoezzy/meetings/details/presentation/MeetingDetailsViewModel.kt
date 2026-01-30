package dev.whysoezzy.meetings.details.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.domain.models.CommunityHost
import com.whysoezzy.domain.models.MeetingAddress
import com.whysoezzy.domain.models.MeetingInfo
import com.whysoezzy.domain.models.MeetingStatus
import com.whysoezzy.domain.models.MeetingTag
import com.whysoezzy.domain.models.Person
import com.whysoezzy.domain.models.PersonHost
import com.whysoezzy.domain.models.TagState
import dev.whysoezzy.meetings.mappers.toUIKit
import dev.whysoezzy.meetings.mappers.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MeetingDetailsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<MeetingDetailsUiState>(MeetingDetailsUiState.Loading)
    val uiState: StateFlow<MeetingDetailsUiState> = _uiState.asStateFlow()

    fun onEvent(event: MeetingDetailsEvent) {
        when (event) {
            is MeetingDetailsEvent.LoadMeeting -> loadMeeting(event.meetingId)
            MeetingDetailsEvent.JoinMeeting -> joinMeeting()
            MeetingDetailsEvent.LeaveMeeting -> leaveMeeting()
            is MeetingDetailsEvent.NavigateToProfile -> {
                // Навигация к профилю пользователя
            }

            is MeetingDetailsEvent.NavigateToCommunity -> {
                // Навигация к сообществу
            }

            is MeetingDetailsEvent.NavigateToMeeting -> {
                // Навигация к другой встрече
            }

            MeetingDetailsEvent.OpenMap -> {
                // Открытие карты
            }

            MeetingDetailsEvent.ShareMeeting -> {
                // Поделиться встречей
            }
        }
    }

    fun loadMeeting(meetingId: Long) {
        viewModelScope.launch {
            _uiState.value = MeetingDetailsUiState.Loading

            try {
                // Mock data for now
                val mockTags = listOf(
                    MeetingTag(1, "Android", TagState.ACTIVE),
                    MeetingTag(2, "Kotlin", TagState.ACTIVE),
                    MeetingTag(3, "Design", TagState.ACTIVE)
                )

                val mockParticipants = listOf(
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
                        "Разработчик"
                    ),
                    Person(3, "Мария", "Сидорова", "https://picsum.photos/100/100?random=3", "PM"),
                    Person(
                        4,
                        "Алексей",
                        "Козлов",
                        "https://picsum.photos/100/100?random=4",
                        "Аналитик"
                    ),
                    Person(5, "Ольга", "Новикова", "https://picsum.photos/100/100?random=5", "QA")
                )

                val mockOtherMeetings = listOf(
                    MeetingInfo(
                        id = 100,
                        imageUrl = "https://picsum.photos/320/180?random=100",
                        title = "Мастер-класс по Jetpack Compose",
                        address = "ул. Пушкина, 10",
                        tags = mockTags.take(2),
                        time = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000, // +7 дней
                        meetingStatus = MeetingStatus.ACTIVE
                    ),
                    MeetingInfo(
                        id = 101,
                        imageUrl = "https://picsum.photos/320/180?random=101",
                        title = "Конференция Android Dev",
                        address = "ул. Ленина, 5",
                        tags = mockTags,
                        time = System.currentTimeMillis() + 14 * 24 * 60 * 60 * 1000, // +14 дней
                        meetingStatus = MeetingStatus.ACTIVE
                    )
                )

                _uiState.value = MeetingDetailsUiState.Success(
                    meetingId = meetingId,
                    imageUrl = "https://picsum.photos/800/600?random=$meetingId",
                    title = "Встреча разработчиков Android #$meetingId",
                    dateTime = "15 декабря 2024, 19:00",
                    address = MeetingAddress(
                        address = "ул. Тверская, 15, офис 301",
                        latitude = 55.7558,
                        longitude = 37.6176
                    ).toUIKit(),
                    tags = mockTags.toUIKitMeetingTags(),
                    description = "Обсуждение новых технологий...",
                    host = PersonHost(
                        id = 1,
                        name = "Александр",
                        surname = "Петров",
                        description = "Senior Android Developer",
                        imageUrl = "https://picsum.photos/200/200?random=host$meetingId"
                    ).toUIKitPersonHost(),
                    nearestMetro = "М. Охотный ряд",
                    participants = mockParticipants.map { it.toUIKit() },
                    isUserJoined = false,
                    totalPlaces = 30,
                    community = CommunityHost(
                        id = 1,
                        title = "Android Developers Moscow",
                        description = "Сообщество разработчиков...",
                        imageUrl = "https://picsum.photos/300/300?random=community$meetingId",
                        meetingsInfo = mockOtherMeetings
                    ).toUIKitCommunityHost(),
                    otherMeetings = mockOtherMeetings.toUIKitMeetingInfoList()
                )
            } catch (e: Exception) {
                _uiState.value = MeetingDetailsUiState.Error(
                    message = e.message ?: "Не удалось загрузить информацию о встрече"
                )
            }
        }
    }

    private fun joinMeeting() {
        val currentState = _uiState.value
        if (currentState is MeetingDetailsUiState.Success && !currentState.isUserJoined) {
            _uiState.value = currentState.copy(
                isUserJoined = true
            )
        }
    }

    private fun leaveMeeting() {
        val currentState = _uiState.value
        if (currentState is MeetingDetailsUiState.Success && currentState.isUserJoined) {
            _uiState.value = currentState.copy(
                isUserJoined = false
            )
        }
    }
}