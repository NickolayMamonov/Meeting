package dev.whysoezzy.auth.presentation.phone

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.whysoezzy.auth.R
import dev.whysoezzy.uikit.components.buttons.UIKitButton
import dev.whysoezzy.uikit.components.buttons.UIKitButtonState
import dev.whysoezzy.uikit.components.forms.UIKitPhoneInput
import dev.whysoezzy.uikit.components.inputs.UIKitInputState
import dev.whysoezzy.uikit.components.text.TextBody2
import dev.whysoezzy.uikit.components.text.TextHeading1
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.ColorTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens
import org.koin.androidx.compose.koinViewModel

@Composable
private fun phoneErrorText(error: PhoneInputError?): String? = when (error) {
    null -> null
    PhoneInputError.Invalid -> stringResource(R.string.auth_phone_error_invalid)
    is PhoneInputError.Remote -> error.message
}

@Composable
fun PhoneInputScreen(
    onPhoneSubmitted: (String) -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PhoneInputViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isCodeSent) {
        if (uiState.isCodeSent && uiState.phoneNumber.isNotEmpty()) {
            onPhoneSubmitted(uiState.phoneNumber)
        }
    }

    Scaffold { paddingValues ->
        PhoneInputContent(
            uiState = uiState,
            modifier =
                Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
            onPhoneNumberChange = { viewModel.onEvent(PhoneInputEvent.UpdatePhoneNumber(it)) },
            onSendCodeClick = { viewModel.onEvent(PhoneInputEvent.SendCode) },
        )
    }
}

@Composable
private fun PhoneInputContent(
    uiState: PhoneInputUiState,
    modifier: Modifier = Modifier,
    onPhoneNumberChange: (String) -> Unit = {},
    onSendCodeClick: () -> Unit = {},
) {
    Column(
        modifier = modifier.padding(SpacingTokens.L),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.L),
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        // Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M),
        ) {
            TextHeading1(
                text = stringResource(R.string.auth_phone_title),
                color = ColorTokens.NeutralWeak,
                textAlign = TextAlign.Center,
            )

            TextBody2(
                text = stringResource(R.string.auth_phone_subtitle),
                color = ColorTokens.NeutralWeak,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(SpacingTokens.L))

        // Phone input
        val phoneErr = phoneErrorText(uiState.error)
        UIKitPhoneInput(
            value = uiState.phoneNumber,
            onValueChange = onPhoneNumberChange,
            placeholder = stringResource(R.string.auth_phone_placeholder),
            state = if (uiState.error != null) UIKitInputState.ERROR else UIKitInputState.FILLED,
            errorMessage = phoneErr,                          // было: uiState.error
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.weight(1f))

        // Send code button
        UIKitButton(
            text = stringResource(R.string.auth_phone_send_code),
            onClick = onSendCodeClick,
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
private fun PhoneInputScreenPreview() {
    UIKitTheme {
        PhoneInputContent(
            uiState =
                PhoneInputUiState(
                    phoneNumber = "+7 (999) 123-45",
                ),
        )
    }
}

@Preview
@Composable
private fun PhoneInputScreenErrorPreview() {
    UIKitTheme {
        PhoneInputContent(
            uiState =
                PhoneInputUiState(
                    phoneNumber = "+7 (999) 123",
                    error = PhoneInputError.Invalid
                ),
        )
    }
}
