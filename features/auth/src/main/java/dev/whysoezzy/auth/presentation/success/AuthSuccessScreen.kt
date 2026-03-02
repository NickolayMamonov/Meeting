package dev.whysoezzy.auth.presentation.success

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whysoezzy.uikit.components.buttons.UIKitButton
import dev.whysoezzy.uikit.components.buttons.UIKitButtonState
import dev.whysoezzy.uikit.components.text.TextBody1
import dev.whysoezzy.uikit.components.text.TextHeading1
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.ColorTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens

@Composable
fun AuthSuccessScreen(
    onContinueClicked: () -> Unit
) {
    Scaffold { paddingValues ->
        AuthSuccessContent(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            onContinueClicked = onContinueClicked
        )
    }
}

@Composable
private fun AuthSuccessContent(
    modifier: Modifier = Modifier,
    onContinueClicked: () -> Unit = {}
) {
    Column(
        modifier = modifier.padding(SpacingTokens.L),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Success icon
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = "Успех",
            tint = ColorTokens.AccentSuccess,
            modifier = Modifier.size(80.dp)
        )

        Spacer(modifier = Modifier.height(SpacingTokens.L))

        // Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            TextHeading1(
                text = "Добро пожаловать!",
                color = ColorTokens.NeutralWeak,
                textAlign = TextAlign.Center
            )

            TextBody1(
                text = "Регистрация успешно завершена.\nТеперь вы можете пользоваться всеми возможностями приложения.",
                color = ColorTokens.NeutralWeak,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(80.dp))

        // Continue button
        UIKitButton(
            text = "Продолжить",
            onClick = onContinueClicked,
            state = UIKitButtonState.PRIMARY,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview
@Composable
private fun AuthSuccessScreenPreview() {
    UIKitTheme {
        AuthSuccessContent()
    }
}