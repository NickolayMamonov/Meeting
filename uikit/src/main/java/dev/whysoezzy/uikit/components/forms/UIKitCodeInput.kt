package dev.whysoezzy.uikit.components.forms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whysoezzy.uikit.components.text.TextHeading2
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.BorderRadiusTokens
import dev.whysoezzy.uikit.tokens.ColorTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens

@Composable
fun UIKitCodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    codeLength: Int = 4,
    isError: Boolean = false
) {
    Box(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.S, Alignment.CenterHorizontally)
        ) {
            repeat(codeLength) { index ->
                CodeDigitBox(
                    digit = value.getOrNull(index)?.toString() ?: "",
                    isActive = index == value.length,
                    isError = isError
                )
            }
        }

        BasicTextField(
            value = value,
            onValueChange = { newValue ->
                val filtered = newValue.filter { it.isDigit() }
                if (filtered.length <= codeLength) {
                    onValueChange(filtered)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .alpha(0f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            cursorBrush = SolidColor(ColorTokens.BrandDark)
        )
    }
}

@Composable
private fun CodeDigitBox(
    digit: String,
    isActive: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isError -> ColorTokens.AccentDanger
        isActive -> ColorTokens.BrandDark
        digit.isNotEmpty() -> ColorTokens.NeutralWeak
        else -> ColorTokens.NeutralWeak
    }

    val backgroundColor = when {
        isError -> ColorTokens.AccentDanger
        else -> ColorTokens.NeutralWhite
    }

    Box(
        modifier = modifier
            .size(56.dp)
            .clip(RoundedCornerShape(BorderRadiusTokens.M))
            .background(backgroundColor)
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(BorderRadiusTokens.M)
            ),
        contentAlignment = Alignment.Center
    ) {
        TextHeading2(
            text = digit,
            color = ColorTokens.NeutralWeak,
            textAlign = TextAlign.Center
        )
    }
}

@Preview
@Composable
private fun UIKitCodeInputPreview() {
    UIKitTheme {
        UIKitCodeInput(
            value = "12",
            onValueChange = {}
        )
    }
}

@Preview
@Composable
private fun UIKitCodeInputErrorPreview() {
    UIKitTheme {
        UIKitCodeInput(
            value = "1234",
            onValueChange = {},
            isError = true
        )
    }
}