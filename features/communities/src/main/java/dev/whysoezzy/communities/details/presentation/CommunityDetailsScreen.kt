package dev.whysoezzy.communities.details.presentation

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
fun CommunityDetailsScreen(
    communityId: Long,
    onBackPressed: () -> Unit,
    onSubscribersClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CommunityDetailsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold { paddingValues ->
        when (uiState) {
            is CommunityDetailsUiState.Loading -> {
                LoadingContent(modifier = Modifier.padding(paddingValues))
            }

            is CommunityDetailsUiState.Success -> {
                CommunityDetailsContent(
                    communityName = "Сообщество #$communityId",
                    onBackPressed = onBackPressed,
                    onSubscribersClick = onSubscribersClick,
                    modifier = Modifier.padding(paddingValues)
                )
            }

            is CommunityDetailsUiState.Error -> {
                ErrorContent(
                    message = (uiState as CommunityDetailsUiState.Error).message,
                    onRetry = { /* TODO */ },
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
private fun CommunityDetailsContent(
    communityName: String,
    onBackPressed: () -> Unit,
    onSubscribersClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(SpacingTokens.L),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.L)
    ) {
        TextHeading1(
            text = communityName,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Здесь будет информация о сообществе",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        UIKitButton(
            text = "Посмотреть подписчиков",
            onClick = onSubscribersClick,
            modifier = Modifier.fillMaxWidth()
        )

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
        }
    }
}

@Preview
@Composable
private fun CommunityDetailsScreenPreview() {
    UIKitTheme {
        CommunityDetailsContent(
            communityName = "Android Developers",
            onBackPressed = { },
            onSubscribersClick = { }
        )
    }
}