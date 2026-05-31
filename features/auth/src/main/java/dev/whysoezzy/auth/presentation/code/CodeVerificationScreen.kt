package dev.whysoezzy.auth.presentation.code

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
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
import dev.whysoezzy.uikit.components.forms.UIKitCodeInput
import dev.whysoezzy.uikit.components.text.TextBody2
import dev.whysoezzy.uikit.components.text.TextHeading1
import dev.whysoezzy.uikit.components.text.TextMetadata1
import dev.whysoezzy.uikit.security.SecureScreenEffect
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.ColorTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens
import org.koin.androidx.compose.koinViewModel

@Composable
fun CodeVerificationScreen(
    phoneNumber: String,
    onCodeVerifiedExisting: () -> Unit,
    onCodeVerifiedNew: (phone: String, code: String) -> Unit,
    onBackPressed: () -> Unit,
    viewModel: CodeVerificationViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    SecureScreenEffect()

    // Подписываемся на навигационные события из ViewModel
    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.navEvent.collect { event ->
                when (event) {
                    is CodeVerificationNavEvent.NavigateToMain ->
                        onCodeVerifiedExisting()
                    is CodeVerificationNavEvent.NavigateToNameInput ->
                        onCodeVerifiedNew(event.phone, event.code)
                }
            }
        }
    }

    Scaffold { paddingValues ->
        CodeVerificationContent(
            phoneNumber = phoneNumber,
            uiState = uiState,
            modifier =
                Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
            onCodeChange = { viewModel.onEvent(CodeVerificationEvent.UpdateCode(it)) },
            onVerifyClick = { viewModel.onEvent(CodeVerificationEvent.VerifyCode) },
            onResendClick = { viewModel.onEvent(CodeVerificationEvent.ResendCode) },
        )
    }
}

@Composable
private fun CodeVerificationContent(
    phoneNumber: String,
    uiState: CodeVerificationUiState,
    modifier: Modifier = Modifier,
    onCodeChange: (String) -> Unit = {},
    onVerifyClick: () -> Unit = {},
    onResendClick: () -> Unit = {},
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
                text = stringResource(R.string.auth_code_title),
                color = ColorTokens.NeutralWeak,
                textAlign = TextAlign.Center,
            )
            TextBody2(
                text = stringResource(R.string.auth_code_subtitle, phoneNumber),
                color = ColorTokens.NeutralWeak,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(SpacingTokens.L))

        UIKitCodeInput(
            value = uiState.code,
            onValueChange = onCodeChange,
            isError = uiState.error != null,
        )

        if (uiState.error != null) {
            TextMetadata1(
                text = uiState.error,
                color = ColorTokens.AccentDanger,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(SpacingTokens.M))

        if (uiState.canResend) {
            TextButton(onClick = onResendClick) {
                TextMetadata1(
                    text = stringResource(R.string.auth_code_resend),
                    color = ColorTokens.BrandDefault,
                )
            }
        } else {
            TextMetadata1(
                text = stringResource(R.string.auth_code_resend_timer, uiState.remainingTime),
                color = ColorTokens.NeutralWeak,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        UIKitButton(
            text = stringResource(R.string.auth_code_confirm),
            onClick = onVerifyClick,
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
private fun CodeVerificationScreenPreview() {
    UIKitTheme {
        CodeVerificationContent(
            phoneNumber = stringResource(R.string.auth_code_error_invalid),
            uiState = CodeVerificationUiState(code = "12", remainingTime = 45),
        )
    }
}

@Preview
@Composable
private fun CodeVerificationScreenErrorPreview() {
    UIKitTheme {
        CodeVerificationContent(
            phoneNumber = "+7 (999) 123-45-67",
            uiState =
                CodeVerificationUiState(
                    code = "1234",
                    error = stringResource(R.string.auth_code_error_invalid),
                    canResend = true,
                ),
        )
    }
}
