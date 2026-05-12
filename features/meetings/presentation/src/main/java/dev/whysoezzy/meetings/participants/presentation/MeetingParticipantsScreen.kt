package dev.whysoezzy.meetings.participants.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.whysoezzy.uikit.components.layouts.PersonItem
import dev.whysoezzy.uikit.components.layouts.PersonsGridContent
import dev.whysoezzy.uikit.components.layouts.PersonsGridError
import dev.whysoezzy.uikit.components.layouts.PersonsGridLoading
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingParticipantsScreen(
    meetingId: Long,
    onBackPressed: () -> Unit,
    onPersonClick: (Long) -> Unit = {},
    viewModel: MeetingParticipantsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(meetingId) {
        viewModel.onEvent(MeetingParticipantsEvent.LoadParticipants(meetingId))
    }

    LaunchedEffect(Unit) {
        viewModel.navEvent.collect { event ->
            when (event) {
                is MeetingParticipantsNavEvent.NavigateToProfile -> onPersonClick(event.userId)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (uiState) {
                            is MeetingParticipantsUiState.Success ->
                                (uiState as MeetingParticipantsUiState.Success).meetingTitle
                            else -> "Участники"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Назад")
                    }
                },
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is MeetingParticipantsUiState.Loading -> {
                PersonsGridLoading(modifier = Modifier.padding(paddingValues))
            }

            is MeetingParticipantsUiState.Success -> {
                PersonsGridContent(
                    persons = state.participants.map { participant ->
                        PersonItem(
                            id = participant.id,
                            name = "${participant.name} ${participant.surname}",
                            role = participant.role,
                            imageUrl = participant.avatarUrl
                        )
                    },
                    onPersonClick = { participantId ->
                        viewModel.onEvent(MeetingParticipantsEvent.NavigateToProfile(participantId))
                    },
                    emptyStateText = "Пока нет участников",
                    modifier = Modifier.padding(paddingValues)
                )
            }

            is MeetingParticipantsUiState.Error -> {
                PersonsGridError(
                    message = state.message,
                    onRetry = {
                        viewModel.onEvent(MeetingParticipantsEvent.LoadParticipants(meetingId))
                    },
                    onBackPressed = onBackPressed,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}
