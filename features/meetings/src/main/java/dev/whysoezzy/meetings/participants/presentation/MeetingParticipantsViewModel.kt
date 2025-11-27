package dev.whysoezzy.meetings.participants.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.whysoezzy.domain.models.Person
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MeetingParticipantsViewModel : ViewModel() {

    private val _uiState =
        MutableStateFlow<MeetingParticipantsUiState>(MeetingParticipantsUiState.Loading)
    val uiState: StateFlow<MeetingParticipantsUiState> = _uiState.asStateFlow()

    fun onEvent(event: MeetingParticipantsEvent) {
        when (event) {
            is MeetingParticipantsEvent.LoadParticipants -> loadParticipants(event.meetingId)
            is MeetingParticipantsEvent.NavigateToProfile -> {
                // Навигация к профилю
            }
        }
    }

    fun loadParticipants(meetingId: Long) {
        viewModelScope.launch {
            _uiState.value = MeetingParticipantsUiState.Loading

            try {
                // Mock data for now
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
                        "Android разработчик"
                    ),
                    Person(
                        3,
                        "Мария",
                        "Сидорова",
                        "https://picsum.photos/100/100?random=3",
                        "Product Manager"
                    ),
                    Person(
                        4,
                        "Алексей",
                        "Козлов",
                        "https://picsum.photos/100/100?random=4",
                        "Аналитик"
                    ),
                    Person(
                        5,
                        "Ольга",
                        "Новикова",
                        "https://picsum.photos/100/100?random=5",
                        "QA Engineer"
                    ),
                    Person(
                        6,
                        "Дмитрий",
                        "Кузнецов",
                        "https://picsum.photos/100/100?random=6",
                        "UX Designer"
                    ),
                    Person(
                        7,
                        "Елена",
                        "Федорова",
                        "https://picsum.photos/100/100?random=7",
                        "Backend Dev"
                    ),
                    Person(
                        8,
                        "Максим",
                        "Орлов",
                        "https://picsum.photos/100/100?random=8",
                        "DevOps"
                    ),
                    Person(
                        9,
                        "Наталья",
                        "Ковалева",
                        "https://picsum.photos/100/100?random=9",
                        "Team Lead"
                    )
                )

                _uiState.value = MeetingParticipantsUiState.Success(
                    meetingTitle = "Встреча разработчиков #$meetingId",
                    participants = mockParticipants
                )
            } catch (e: Exception) {
                _uiState.value = MeetingParticipantsUiState.Error(
                    message = e.message ?: "Не удалось загрузить участников"
                )
            }
        }
    }
}