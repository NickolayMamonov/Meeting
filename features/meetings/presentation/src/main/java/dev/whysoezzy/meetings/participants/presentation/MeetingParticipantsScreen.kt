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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.whysoezzy.features_meetings.R
import dev.whysoezzy.uikit.components.layouts.PersonItem
import dev.whysoezzy.uikit.components.layouts.PersonsGridContent
import dev.whysoezzy.uikit.components.layouts.PersonsGridError
import dev.whysoezzy.uikit.components.layouts.PersonsGridLoading
import org.koin.androidx.compose.koinViewModel
import dev.whysoezzy.uikit.R as UIKitR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingParticipantsScreen(
    meetingId: Long,
    onBackPressed: () -> Unit,
    onPersonClick: (Long) -> Unit = {},
    viewModel: MeetingParticipantsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(meetingId) {
        viewModel.onEvent(MeetingParticipantsEvent.LoadParticipants(meetingId))
    }

    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.navEvent.collect { event ->
                when (event) {
                    is MeetingParticipantsNavEvent.NavigateToProfile -> onPersonClick(event.userId)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.meeting_participants_default_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = stringResource(UIKitR.string.action_back))
                    }
                },
            )
        },
    ) { paddingValues ->
        when (val state = uiState) {
            is MeetingParticipantsUiState.Loading -> {
                PersonsGridLoading(modifier = Modifier.padding(paddingValues))
            }

            is MeetingParticipantsUiState.Success -> {
                PersonsGridContent(
                    persons = state.participants,
                    onPersonClick = { participantId ->
                        viewModel.onEvent(MeetingParticipantsEvent.NavigateToProfile(participantId))
                    },
                    emptyStateText = stringResource(R.string.meeting_participants_empty),
                    modifier = Modifier.padding(paddingValues),
                )
            }

            is MeetingParticipantsUiState.Error -> {
                PersonsGridError(
                    message = state.message,
                    onRetry = {
                        viewModel.onEvent(MeetingParticipantsEvent.LoadParticipants(meetingId))
                    },
                    onBackPressed = onBackPressed,
                    modifier = Modifier.padding(paddingValues),
                )
            }
        }
    }
}
