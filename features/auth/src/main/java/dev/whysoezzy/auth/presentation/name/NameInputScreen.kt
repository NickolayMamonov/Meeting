package dev.whysoezzy.auth.presentation.name

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whysoezzy.uikit.components.buttons.UIKitButton
import dev.whysoezzy.uikit.components.buttons.UIKitButtonState
import dev.whysoezzy.uikit.components.inputs.UIKitInput
import dev.whysoezzy.uikit.components.text.TextBody2
import dev.whysoezzy.uikit.components.text.TextHeading1
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.ColorTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens
import org.koin.androidx.compose.koinViewModel

@Composable
fun NameInputScreen(
    onNameSubmitted: () -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NameInputViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isSubmitted) {
        if (uiState.isSubmitted) {
            onNameSubmitted()
        }
    }

    Scaffold { paddingValues ->
        NameInputContent(
            uiState = uiState,
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            onNameChange = { viewModel.onEvent(NameInputEvent.UpdateName(it)) },
            onSurnameChange = { viewModel.onEvent(NameInputEvent.UpdateSurname(it)) },
            onContinueClick = { viewModel.onEvent(NameInputEvent.Continue) }
        )
    }
}

@Composable
private fun NameInputContent(
    uiState: NameInputUiState,
    modifier: Modifier = Modifier,
    onNameChange: (String) -> Unit = {},
    onSurnameChange: (String) -> Unit = {},
    onContinueClick: () -> Unit = {}
) {
    Column(
        modifier = modifier.padding(SpacingTokens.L),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.L)
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        // Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            TextHeading1(
                text = "Знакомство",
                color = ColorTokens.NeutralWeak,
                textAlign = TextAlign.Center
            )

            TextBody2(
                text = "Расскажите нам как к вам обращаться",
                color = ColorTokens.NeutralWeak,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(SpacingTokens.L))

        // Input fields
        Column(
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            uiState.nameError?.let {
                UIKitInput(
                    value = uiState.name,
                    onValueChange = onNameChange,
                    isError = true,
                    errorMessage = it,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            uiState.surnameError?.let {
                UIKitInput(
                    value = uiState.surname,
                    onValueChange = onNameChange,
                    isError = true,
                    errorMessage = it,
                    modifier = Modifier.fillMaxWidth()
                )
            }

        }

        Spacer(modifier = Modifier.weight(1f))

        // Continue button
        UIKitButton(
            text = "Продолжить",
            onClick = onContinueClick,
            state = if (uiState.isLoading) UIKitButtonState.LOADING
            else if (uiState.isValid) UIKitButtonState.PRIMARY
            else UIKitButtonState.DISABLED,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview
@Composable
private fun NameInputScreenPreview() {
    UIKitTheme {
        NameInputContent(
            uiState = NameInputUiState(
                name = "Иван",
                surname = "Петров"
            )
        )
    }
}

@Preview
@Composable
private fun NameInputScreenErrorPreview() {
    UIKitTheme {
        NameInputContent(
            uiState = NameInputUiState(
                name = "И",
                surname = "",
                nameError = "Имя должно содержать минимум 2 символа",
                surnameError = "Фамилия не может быть пустой"
            )
        )
    }
}