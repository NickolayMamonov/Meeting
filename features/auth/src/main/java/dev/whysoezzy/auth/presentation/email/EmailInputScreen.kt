package dev.whysoezzy.auth.presentation.email

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whysoezzy.auth.domain.models.AuthFailure
import dev.whysoezzy.auth.R
import dev.whysoezzy.uikit.components.buttons.UIKitButton
import dev.whysoezzy.uikit.components.buttons.UIKitButtonState
import dev.whysoezzy.uikit.components.inputs.UIKitInput
import dev.whysoezzy.uikit.components.text.TextBody2
import dev.whysoezzy.uikit.components.text.TextHeading1
import dev.whysoezzy.uikit.security.SecureScreenEffect
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.ColorTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens
import org.koin.androidx.compose.koinViewModel

@Composable
fun EmailInputScreen(
    onAttemptStarted: (String) -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EmailInputViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SecureScreenEffect()
    LaunchedEffect(Unit) {
        viewModel.navEvent.collect { event ->
            when (event) {
                is EmailInputNavEvent.NavigateToCode -> onAttemptStarted(event.attemptId)
            }
        }
    }
    Scaffold { padding ->
        EmailInputContent(
            state = state,
            modifier = modifier.padding(padding).fillMaxSize(),
            onEmailChange = { viewModel.onEvent(EmailInputEvent.UpdateEmail(it)) },
            onSubmit = { viewModel.onEvent(EmailInputEvent.Submit) },
        )
    }
}

@Composable
private fun EmailInputContent(
    state: EmailInputUiState,
    modifier: Modifier = Modifier,
    onEmailChange: (String) -> Unit = {},
    onSubmit: () -> Unit = {},
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
                text = stringResource(R.string.auth_email_title),
                color = ColorTokens.NeutralWeak,
                textAlign = TextAlign.Center,
            )
            TextBody2(
                text = stringResource(R.string.auth_email_subtitle),
                color = ColorTokens.NeutralWeak,
                textAlign = TextAlign.Center,
            )
        }
        UIKitInput(
            value = state.email,
            onValueChange = onEmailChange,
            placeholder = stringResource(R.string.auth_email_placeholder),
            hint = stringResource(R.string.auth_email_placeholder),
            isError = state.error != null,
            errorMessage = state.error?.let(::emailFailureMessage) ?: "",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            contentType = ContentType.EmailAddress,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.weight(1f))
        UIKitButton(
            text = stringResource(R.string.auth_email_send_code),
            onClick = onSubmit,
            state = if (state.isLoading) {
                UIKitButtonState.LOADING
            } else if (state.email.isNotBlank() && state.error == null) {
                UIKitButtonState.PRIMARY
            } else {
                UIKitButtonState.DISABLED
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun emailFailureMessage(failure: AuthFailure): String = when (failure) {
    AuthFailure.InvalidEmail -> "Введите корректный email"
    AuthFailure.RateLimited -> "Слишком много попыток. Повторите позже"
    AuthFailure.DeliveryUnavailable -> "Не удалось доставить код. Попробуйте ещё раз"
    AuthFailure.ActivationUnavailable -> "Сервис отправки временно недоступен"
    AuthFailure.NoConnection, AuthFailure.Server, AuthFailure.Unknown ->
        "Не удалось выполнить запрос. Попробуйте ещё раз"
    else -> "Не удалось выполнить запрос. Попробуйте ещё раз"
}

@Preview
@Composable
private fun EmailInputPreview() {
    UIKitTheme { EmailInputContent(EmailInputUiState(email = "person@example.com")) }
}
