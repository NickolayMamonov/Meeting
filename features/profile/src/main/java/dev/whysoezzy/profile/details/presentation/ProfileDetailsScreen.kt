package dev.whysoezzy.profile.details.presentation

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
fun ProfileDetailsScreen(
    onBackPressed: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileDetailsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold { paddingValues ->
        when (uiState) {
            is ProfileDetailsUiState.Loading -> {
                LoadingContent(modifier = Modifier.padding(paddingValues))
            }

            is ProfileDetailsUiState.Success -> {
                ProfileContent(
                    userName = (uiState as ProfileDetailsUiState.Success).userName,
                    userEmail = (uiState as ProfileDetailsUiState.Success).userEmail,
                    onBackPressed = onBackPressed,
                    onEditClick = onEditClick,
                    modifier = Modifier.padding(paddingValues)
                )
            }

            is ProfileDetailsUiState.Error -> {
                ErrorContent(
                    message = (uiState as ProfileDetailsUiState.Error).message,
                    onRetry = { viewModel.loadProfile() },
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
private fun ProfileContent(
    userName: String,
    userEmail: String,
    onBackPressed: () -> Unit,
    onEditClick: () -> Unit,
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
            text = "Профиль",
            textAlign = TextAlign.Center
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            Text(
                text = "Имя: $userName",
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = "Email: $userEmail",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        UIKitButton(
            text = "Редактировать профиль",
            onClick = onEditClick,
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
private fun ProfileDetailsScreenPreview() {
    UIKitTheme {
        ProfileContent(
            userName = "Иван Петров",
            userEmail = "ivan.petrov@example.com",
            onBackPressed = { },
            onEditClick = { }
        )
    }
}