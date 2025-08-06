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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whysoezzy.uikit.components.buttons.UIKitButton
import dev.whysoezzy.uikit.components.buttons.UIKitButtonState
import dev.whysoezzy.uikit.components.forms.UIKitCodeInput
import dev.whysoezzy.uikit.components.text.TextBody2
import dev.whysoezzy.uikit.components.text.TextHeading1
import dev.whysoezzy.uikit.components.text.TextMetadata1
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.ColorTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CodeVerificationScreen(
    phoneNumber: String,
    onCodeVerified: () -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CodeVerificationViewModel = koinViewModel { parametersOf(phoneNumber) }
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isVerified) {
        if (uiState.isVerified) {
            onCodeVerified()
        }
    }

    Scaffold { paddingValues ->
        CodeVerificationContent(
            phoneNumber = phoneNumber,
            uiState = uiState,
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            onCodeChange = { viewModel.onEvent(CodeVerificationEvent.UpdateCode(it)) },
            onVerifyClick = { viewModel.onEvent(CodeVerificationEvent.VerifyCode) },
            onResendClick = { viewModel.onEvent(CodeVerificationEvent.ResendCode) }
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
    onResendClick: () -> Unit = {}
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
                text = "Введите код",
                color = ColorTokens.NeutralWeak,
                textAlign = TextAlign.Center
            )

            TextBody2(
                text = "Мы отправили код подтверждения на номер\n$phoneNumber",
                color = ColorTokens.NeutralWeak,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(SpacingTokens.L))

        // Code input
        UIKitCodeInput(
            value = uiState.code,
            onValueChange = onCodeChange,
            isError = uiState.error != null
        )

        // Error message
        if (uiState.error != null) {
            TextMetadata1(
                text = uiState.error,
                color = ColorTokens.AccentDanger,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(SpacingTokens.M))

        // Resend code
        if (uiState.canResend) {
            TextButton(onClick = onResendClick) {
                TextMetadata1(
                    text = "Отправить код повторно",
                    color = ColorTokens.BrandDefault
                )
            }
        } else {
            TextMetadata1(
                text = "Отправить повторно через ${uiState.remainingTime} сек",
                color = ColorTokens.NeutralWeak,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Verify button
        UIKitButton(
            text = "Подтвердить",
            onClick = onVerifyClick,
            state = when {
                uiState.isLoading -> UIKitButtonState.LOADING
                uiState.isValid -> UIKitButtonState.PRIMARY
                else -> UIKitButtonState.DISABLED
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview
@Composable
private fun CodeVerificationScreenPreview() {
    UIKitTheme {
        CodeVerificationContent(
            phoneNumber = "+7 (999) 123-45-67",
            uiState = CodeVerificationUiState(
                code = "12",
                remainingTime = 45
            )
        )
    }
}

@Preview
@Composable
private fun CodeVerificationScreenErrorPreview() {
    UIKitTheme {
        CodeVerificationContent(
            phoneNumber = "+7 (999) 123-45-67",
            uiState = CodeVerificationUiState(
                code = "1234",
                error = "Неверный код подтверждения",
                canResend = true
            )
        )
    }
}