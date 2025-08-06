package dev.whysoezzy.meetings.participants.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import dev.whysoezzy.uikit.components.buttons.UIKitButton
import dev.whysoezzy.uikit.components.text.TextHeading1
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.SpacingTokens
import org.koin.androidx.compose.koinViewModel

@Composable
fun MeetingParticipantsScreen(
    meetingId: Long,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MeetingParticipantsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(meetingId) {
        viewModel.onEvent(MeetingParticipantsEvent.LoadParticipants(meetingId))
    }

    Scaffold { paddingValues ->
        when (uiState) {
            is MeetingParticipantsUiState.Loading -> {
                LoadingContent(modifier = Modifier.padding(paddingValues))
            }

            is MeetingParticipantsUiState.Success -> {
                ParticipantsContent(
                    participants = (uiState as MeetingParticipantsUiState.Success).participants,
                    onBackPressed = onBackPressed,
                    modifier = Modifier.padding(paddingValues)
                )
            }

            is MeetingParticipantsUiState.Error -> {
                ErrorContent(
                    message = (uiState as MeetingParticipantsUiState.Error).message,
                    onRetry = {
                        viewModel.onEvent(
                            MeetingParticipantsEvent.LoadParticipants(
                                meetingId
                            )
                        )
                    },
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
private fun ParticipantsContent(
    participants: List<Participant>,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(SpacingTokens.L)
    ) {
        TextHeading1(
            text = "Участники встречи",
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Всего участников: ${participants.size}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = SpacingTokens.M)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.S)
        ) {
            items(participants) { participant ->
                ParticipantCard(
                    participant = participant,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (participants.isEmpty()) {
                item {
                    Text(
                        text = "Пока нет участников",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(SpacingTokens.L)
                    )
                }
            }
        }

        UIKitButton(
            text = "Назад",
            onClick = onBackPressed,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ParticipantCard(
    participant: Participant,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(SpacingTokens.M),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            // Avatar placeholder
            Text(
                text = participant.name.first().toString(),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .padding(SpacingTokens.S)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = participant.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (participant.isHost) FontWeight.Bold else FontWeight.Normal
                    )
                )

                if (participant.isHost) {
                    Text(
                        text = "Организатор",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
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
private fun MeetingParticipantsScreenPreview() {
    UIKitTheme {
        ParticipantsContent(
            participants = listOf(
                Participant(1, "Иван Петров", isHost = true),
                Participant(2, "Мария Сидорова"),
                Participant(3, "Алексей Кузнецов"),
                Participant(4, "Елена Васильева")
            ),
            onBackPressed = { }
        )
    }
}