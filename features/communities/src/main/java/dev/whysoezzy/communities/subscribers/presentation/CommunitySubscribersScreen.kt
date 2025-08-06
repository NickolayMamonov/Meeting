package dev.whysoezzy.communities.subscribers.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import dev.whysoezzy.uikit.components.buttons.UIKitButton
import dev.whysoezzy.uikit.components.text.TextHeading1
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.SpacingTokens
import org.koin.androidx.compose.koinViewModel

@Composable
fun CommunitySubscribersScreen(
    communityId: Long,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CommunitySubscribersViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold { paddingValues ->
        when (uiState) {
            is CommunitySubscribersUiState.Loading -> {
                LoadingContent(modifier = Modifier.padding(paddingValues))
            }

            is CommunitySubscribersUiState.Success -> {
                SubscribersContent(
                    communityId = communityId,
                    subscribers = (uiState as CommunitySubscribersUiState.Success).subscribers,
                    onBackPressed = onBackPressed,
                    modifier = Modifier.padding(paddingValues)
                )
            }

            is CommunitySubscribersUiState.Error -> {
                ErrorContent(
                    message = (uiState as CommunitySubscribersUiState.Error).message,
                    onRetry = { /* TODO */ },
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
private fun SubscribersContent(
    communityId: Long,
    subscribers: List<String>,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(SpacingTokens.L)
    ) {
        TextHeading1(
            text = "Подписчики сообщества #$communityId",
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            items(subscribers) { subscriber ->
                Text(
                    text = subscriber,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(SpacingTokens.M)
                )
            }

            if (subscribers.isEmpty()) {
                item {
                    Text(
                        text = "Пока нет подписчиков",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
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
private fun CommunitySubscribersScreenPreview() {
    UIKitTheme {
        SubscribersContent(
            communityId = 1,
            subscribers = listOf("Иван Петров", "Мария Сидорова", "Алексей Кузнецов"),
            onBackPressed = { }
        )
    }
}