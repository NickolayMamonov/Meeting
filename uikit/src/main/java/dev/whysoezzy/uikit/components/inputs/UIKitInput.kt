package dev.whysoezzy.uikit.components.inputs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.BorderRadiusTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens

enum class UIKitInputState {
    EMPTY,
    FOCUSED,
    FILLED,
    ERROR
}

@Composable
fun UIKitInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    maxLines: Int = 1,
    errorMessage: String = ""
) {
    val colorScheme = UIKitTheme.colors
    var isFocused by remember { mutableStateOf(false) }

    val inputState = when {
        isError -> UIKitInputState.ERROR
        isFocused -> UIKitInputState.FOCUSED
        value.isNotEmpty() -> UIKitInputState.FILLED
        else -> UIKitInputState.EMPTY
    }

    Column {
        Box(
            modifier = modifier
                .width(343.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(BorderRadiusTokens.L))
                .background(
                    when (inputState) {
                        UIKitInputState.ERROR -> Color(0xFFFDE7ED)
                        else -> colorScheme.neutralSecondaryBackground
                    },
                    shape = RoundedCornerShape(BorderRadiusTokens.L)
                )
                .then(
                    when (inputState) {
                        UIKitInputState.FOCUSED -> Modifier.border(
                            width = 1.dp,
                            color = colorScheme.brandDefault,
                            shape = RoundedCornerShape(BorderRadiusTokens.L)
                        )

                        UIKitInputState.ERROR -> Modifier.border(
                            width = 1.dp,
                            color = colorScheme.accentDanger,
                            shape = RoundedCornerShape(BorderRadiusTokens.L)
                        )

                        else -> Modifier
                    }
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = SpacingTokens.M, vertical = SpacingTokens.S),
                verticalArrangement = Arrangement.Center
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused },
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        color = when {
                            isError -> colorScheme.accentDanger
                            else -> colorScheme.neutralBody
                        }
                    ),
                    cursorBrush = SolidColor(colorScheme.brandDefault),
                    keyboardOptions = keyboardOptions,
                    visualTransformation = visualTransformation,
                    maxLines = maxLines,
                    decorationBox = { innerTextField ->
                        Box {
                            if (value.isEmpty() && !isFocused) {
                                Text(
                                    text = hint,
                                    color = colorScheme.neutralWeak,
                                    fontSize = 16.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }

        if (isError && errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                color = colorScheme.accentDanger,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = SpacingTokens.M, top = SpacingTokens.XS)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UIKitInputPreview() {
    UIKitTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingTokens.M),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            // Empty state
            var emptyText by remember { mutableStateOf("") }
            UIKitInput(
                value = emptyText,
                onValueChange = { emptyText = it },
                hint = "Enter text"
            )

            // Filled state
            var filledText by remember { mutableStateOf("Some text") }
            UIKitInput(
                value = filledText,
                onValueChange = { filledText = it },
                hint = "Enter text"
            )

            // Error state
            var errorText by remember { mutableStateOf("Invalid input") }
            UIKitInput(
                value = errorText,
                onValueChange = { errorText = it },
                hint = "Enter text",
                isError = true,
                errorMessage = "Error message"
            )
        }
    }
}