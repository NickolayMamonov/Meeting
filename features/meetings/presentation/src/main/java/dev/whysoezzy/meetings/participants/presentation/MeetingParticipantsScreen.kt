package dev.whysoezzy.meetings.participants.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.whysoezzy.domain.models.Person
import dev.whysoezzy.uikit.components.buttons.UIKitButton
import dev.whysoezzy.uikit.components.cards.UIKitPersonCard
import dev.whysoezzy.uikit.components.text.TextHeading1
import dev.whysoezzy.uikit.components.text.TextMetadata2
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.SpacingTokens
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingParticipantsScreen(
    meetingId: Long,
    onBackPressed: () -> Unit,
    viewModel: MeetingParticipantsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(meetingId) {
        viewModel.onEvent(MeetingParticipantsEvent.LoadParticipants(meetingId))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (uiState) {
                            is MeetingParticipantsUiState.Success -> (uiState as MeetingParticipantsUiState.Success).meetingTitle
                            else -> "Участники"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        when (uiState) {
            is MeetingParticipantsUiState.Loading -> {
                LoadingContent(modifier = Modifier.padding(paddingValues))
            }

            is MeetingParticipantsUiState.Success -> {
                ParticipantsContent(
                    participants = (uiState as MeetingParticipantsUiState.Success).participants,
                    onParticipantClick = { participantId ->
                        viewModel.onEvent(MeetingParticipantsEvent.NavigateToProfile(participantId))
                    },
                    modifier = Modifier.padding(paddingValues)
                )
            }

            is MeetingParticipantsUiState.Error -> {
                ErrorContent(
                    message = (uiState as MeetingParticipantsUiState.Error).message,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParticipantsContent(
    participants: List<Person>,
    onParticipantClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = SpacingTokens.L),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.L)
    ) {
        // Participants Grid
        item {
            if (participants.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalArrangement = Arrangement.spacedBy(SpacingTokens.M),
                    maxItemsInEachRow = 3
                ) {
                    participants.forEach { participant ->
                        UIKitPersonCard(
                            name = participant.name,
                            role = participant.bio,
                            imageUrl = participant.avatarUrl,
                            onCardClick = { onParticipantClick(participant.id) }
                        )
                    }
                }
            } else {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SpacingTokens.XL),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Пока нет участников",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Bottom spacer
        item {
            Box(modifier = Modifier.padding(SpacingTokens.L))
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
        val mockParticipants = listOf(
            Person(1, "Анна", "Иванова", "https://picsum.photos/100/100?random=1", "UX Designer","Дизайн"),
            Person(2, "Петр", "Петров", "https://picsum.photos/100/100?random=2", "Android Dev","Разработка"),
            Person(
                3,
                "Мария",
                "Сидорова",
                "https://picsum.photos/100/100?random=3",
                "Product Manager",
                "Разработка"
            ),
            Person(4, "Алексей", "Козлов", "https://picsum.photos/100/100?random=4", "Аналитик","Аналитика"),
            Person(5, "Ольга", "Новикова", "https://picsum.photos/100/100?random=5", "QA Engineer","Тестирование"),
            Person(
                6,
                "Дмитрий",
                "Кузнецов",
                "https://picsum.photos/100/100?random=6",
                "Backend Dev",
                "Разработка"
            )
        )
        
        ParticipantsContent(
            participants = mockParticipants,
            onParticipantClick = { }
        )
    }
}
