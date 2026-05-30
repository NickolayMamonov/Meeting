package dev.whysoezzy.auth.presentation.name

import androidx.annotation.StringRes
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.whysoezzy.auth.R
import dev.whysoezzy.uikit.components.buttons.UIKitButton
import dev.whysoezzy.uikit.components.buttons.UIKitButtonState
import dev.whysoezzy.uikit.components.inputs.UIKitInput
import dev.whysoezzy.uikit.components.text.TextBody2
import dev.whysoezzy.uikit.components.text.TextHeading1
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.ColorTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens
import org.koin.androidx.compose.koinViewModel
import dev.whysoezzy.uikit.R as UIKitR

@Composable
fun NameInputScreen(
    onNameSubmitted: () -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NameInputViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.navEvent.collect { event ->
                when (event) {
                    is NameInputNavEvent.NavigateToSuccess -> onNameSubmitted()
                }
            }
        }
    }

    Scaffold { paddingValues ->
        NameInputContent(
            uiState = uiState,
            modifier =
                Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
            onNameChange = { viewModel.onEvent(NameInputEvent.UpdateName(it)) },
            onSurnameChange = { viewModel.onEvent(NameInputEvent.UpdateSurname(it)) },
            onContinueClick = { viewModel.onEvent(NameInputEvent.Continue) },
        )
    }
}

@Composable
private fun NameInputContent(
    uiState: NameInputUiState,
    modifier: Modifier = Modifier,
    onNameChange: (String) -> Unit = {},
    onSurnameChange: (String) -> Unit = {},
    onContinueClick: () -> Unit = {},
) {
    Column(
        modifier = modifier.padding(SpacingTokens.L),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.L),
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M),
        ) {
            TextHeading1(
                text = stringResource(R.string.auth_name_title),
                color = ColorTokens.NeutralWeak,
                textAlign = TextAlign.Center,
            )
            TextBody2(
                text = stringResource(R.string.auth_name_subtitle),
                color = ColorTokens.NeutralWeak,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(SpacingTokens.L))

        Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)) {
            val nameErr = nameErrorText(uiState.nameError, blankRes = R.string.auth_name_error_blank)
            val surnameErr = nameErrorText(uiState.surnameError, blankRes = R.string.auth_surname_error_blank)
            UIKitInput(
                value = uiState.name,
                onValueChange = onNameChange,
                hint = stringResource(R.string.auth_name_hint_first),
                isError = uiState.nameError != null,
                errorMessage = nameErr ?: "",
                modifier = Modifier.fillMaxWidth(),
            )
            UIKitInput(
                value = uiState.surname,
                onValueChange = onSurnameChange,
                hint = stringResource(R.string.auth_name_hint_last),
                isError = uiState.surnameError != null,
                errorMessage = surnameErr ?: "",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        UIKitButton(
            text = stringResource(UIKitR.string.action_continue),
            onClick = onContinueClick,
            state =
                when {
                    uiState.isLoading -> UIKitButtonState.LOADING
                    uiState.isValid -> UIKitButtonState.PRIMARY
                    else -> UIKitButtonState.DISABLED
                },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview
@Composable
private fun NameInputScreenPreview() {
    UIKitTheme {
        NameInputContent(uiState = NameInputUiState(name = "Иван", surname = "Петров"))
    }
}

@Preview
@Composable
private fun NameInputScreenErrorPreview() {
    UIKitTheme {
        NameInputContent(
            uiState =
                NameInputUiState(
                    name = "И",
                    surname = "",
                    nameError = NameFieldError.TooShort,
                    surnameError = NameFieldError.Blank,
                ),
        )
    }
}

@Composable
private fun nameErrorText(error: NameFieldError?, @StringRes blankRes: Int): String? = when (error) {
    null -> null
    NameFieldError.Blank -> stringResource(blankRes)
    NameFieldError.TooShort -> stringResource(R.string.auth_name_error_too_short)
    NameFieldError.NonLetter -> stringResource(R.string.auth_name_error_only_letters)
    is NameFieldError.Remote -> error.message
}