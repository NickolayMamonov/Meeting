package dev.whysoezzy.auth.presentation.code

import androidx.activity.compose.BackHandler
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whysoezzy.auth.domain.models.AuthFailure
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
import org.koin.core.parameter.parametersOf

@Composable
fun CodeVerificationScreen(
    attemptId: String,
    onCodeVerifiedExisting: () -> Unit,
    onCodeVerifiedNew: () -> Unit,
    onBackPressed: () -> Unit,
    viewModel: CodeVerificationViewModel = koinViewModel { parametersOf(attemptId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SecureScreenEffect()
    BackHandler { viewModel.clearAndLeave() }
    LaunchedEffect(Unit) {
        viewModel.navEvent.collect { event ->
            when (event) {
                CodeVerificationNavEvent.NavigateToMain -> onCodeVerifiedExisting()
                CodeVerificationNavEvent.NavigateToNameInput -> onCodeVerifiedNew()
                CodeVerificationNavEvent.NavigateToEmail -> onBackPressed()
            }
        }
    }
    Scaffold { padding ->
        CodeVerificationContent(
            maskedEmail = state.maskedEmail,
            uiState = state,
            modifier = Modifier.padding(padding).fillMaxSize(),
            onCodeChange = { viewModel.onEvent(CodeVerificationEvent.UpdateCode(it)) },
            onVerifyClick = { viewModel.onEvent(CodeVerificationEvent.VerifyCode) },
            onResendClick = { viewModel.onEvent(CodeVerificationEvent.ResendCode) },
        )
    }
}

@Composable
private fun CodeVerificationContent(
    maskedEmail: String,
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
        Spacer(Modifier.height(60.dp))
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
                text = stringResource(R.string.auth_code_subtitle, maskedEmail),
                color = ColorTokens.NeutralWeak,
                textAlign = TextAlign.Center,
            )
        }
        UIKitCodeInput(
            value = uiState.code,
            onValueChange = onCodeChange,
            codeLength = 6,
            isError = uiState.error != null,
        )
        uiState.error?.let { failure ->
            TextMetadata1(
                text = failureMessage(failure),
                color = ColorTokens.AccentDanger,
                textAlign = TextAlign.Center,
            )
        }
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
        Spacer(Modifier.weight(1f))
        UIKitButton(
            text = stringResource(R.string.auth_code_confirm),
            onClick = onVerifyClick,
            state = if (uiState.isLoading) {
                UIKitButtonState.LOADING
            } else if (uiState.isValid) {
                UIKitButtonState.PRIMARY
            } else {
                UIKitButtonState.DISABLED
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun failureMessage(failure: AuthFailure): String = when (failure) {
    AuthFailure.InvalidOrExpiredCode,
    AuthFailure.InvalidCode,
    -> "The code is incorrect or expired. Request a new code"
    AuthFailure.RateLimited -> "Too many attempts. Try again later"
    AuthFailure.DeliveryUnavailable -> "Code delivery is temporarily unavailable"
    AuthFailure.ActivationUnavailable -> "The email service is temporarily unavailable"
    AuthFailure.NoConnection, AuthFailure.Server, AuthFailure.Unknown ->
        "The request could not be completed. Try again"
    else -> "The request could not be completed. Try again"
}

@Preview
@Composable
private fun CodeVerificationPreview() {
    UIKitTheme {
        CodeVerificationContent(
            maskedEmail = "p***@example.com",
            uiState = CodeVerificationUiState(code = "123456", remainingTime = 45),
        )
    }
}
