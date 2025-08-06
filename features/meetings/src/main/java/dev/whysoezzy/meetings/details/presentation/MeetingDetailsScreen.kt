package dev.whysoezzy.meetings.details.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import dev.whysoezzy.uikit.components.buttons.UIKitButton
import dev.whysoezzy.uikit.components.buttons.UIKitButtonState
import dev.whysoezzy.uikit.components.text.TextBody1
import dev.whysoezzy.uikit.components.text.TextHeading1
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.SpacingTokens
import org.koin.androidx.compose.koinViewModel

@Composable
fun MeetingDetailsScreen(
    meetingId: Long,
    onBackPressed: () -> Unit,
    onParticipantsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MeetingDetailsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(meetingId) {
        viewModel.onEvent(MeetingDetailsEvent.LoadMeeting(meetingId))
    }

    Scaffold { paddingValues ->
        when (uiState) {
            is MeetingDetailsUiState.Loading -> {
                LoadingContent(modifier = Modifier.padding(paddingValues))
            }

            is MeetingDetailsUiState.Success -> {
                MeetingContent(
                    uiState = uiState as MeetingDetailsUiState.Success,
                    onBackPressed = onBackPressed,
                    onParticipantsClick = onParticipantsClick,
                    onJoinClick = { viewModel.onEvent(MeetingDetailsEvent.JoinMeeting) },
                    onLeaveClick = { viewModel.onEvent(MeetingDetailsEvent.LeaveMeeting) },
                    modifier = Modifier.padding(paddingValues)
                )
            }

            is MeetingDetailsUiState.Error -> {
                ErrorContent(
                    message = (uiState as MeetingDetailsUiState.Error).message,
                    onRetry = { viewModel.onEvent(MeetingDetailsEvent.LoadMeeting(meetingId)) },
                    onBackPressed = onBackPressed,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
private fun LoadingContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MeetingContent(
    uiState: MeetingDetailsUiState.Success,
    onBackPressed: () -> Unit,
    onParticipantsClick: () -> Unit,
    onJoinClick: () -> Unit,
    onLeaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(SpacingTokens.L),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.L)
    ) {
        // Meeting info
        Column(
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            TextHeading1(
                text = uiState.meetingTitle,
                textAlign = TextAlign.Start
            )

            TextBody1(
                text = uiState.meetingDescription,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "📅 ${uiState.meetingDate}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "📍 ${uiState.meetingAddress}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "👥 Участников: ${uiState.participantsCount}",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Action buttons
        Column(
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            if (uiState.isUserJoined) {
                UIKitButton(
                    text = "Покинуть встречу",
                    onClick = onLeaveClick,
                    state = UIKitButtonState.PRIMARY,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                UIKitButton(
                    text = "Присоединиться к встрече",
                    onClick = onJoinClick,
                    state = UIKitButtonState.SECONDARY,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            UIKitButton(
                text = "Посмотреть участников",
                onClick = onParticipantsClick,
                modifier = Modifier.fillMaxWidth()
            )

            UIKitButton(
                text = "Назад",
                onClick = onBackPressed,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            UIKitButton(
                text = "Повторить",
                onClick = onRetry
            )

            UIKitButton(
                text = "Назад",
                onClick = onBackPressed
            )
        }
    }
}

@Preview
@Composable
private fun MeetingDetailsScreenPreview() {
    UIKitTheme {
        MeetingContent(
            uiState = MeetingDetailsUiState.Success(
                meetingTitle = "Встреча разработчиков Android",
                meetingDescription = "Обсуждение новых технологий и подходов в разработке мобильных приложений",
                meetingDate = "15 декабря 2024, 19:00",
                meetingAddress = "ул. Тверская, 15, офис 301",
                participantsCount = 25,
                isUserJoined = false
            ),
            onBackPressed = { },
            onParticipantsClick = { },
            onJoinClick = { },
            onLeaveClick = { }
        )
    }
}