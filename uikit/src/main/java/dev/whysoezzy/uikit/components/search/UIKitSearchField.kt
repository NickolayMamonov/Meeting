package dev.whysoezzy.uikit.components.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.BorderRadiusTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens
import dev.whysoezzy.uikit.tokens.TypographyTokens

@Composable
fun UIKitSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search...",
    onClear: (() -> Unit)? = null,
    onFocusChange: ((Boolean) -> Unit)? = null
) {
    val colorScheme = UIKitTheme.colors
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(BorderRadiusTokens.S))
            .background(color = colorScheme.neutralSecondaryBackground)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                onFocusChange?.invoke(focusState.isFocused)
            },
        textStyle = TypographyTokens.Subheading2.copy(color = colorScheme.neutralBody),
        cursorBrush = SolidColor(colorScheme.brandDefault),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = { focusManager.clearFocus() }
        ),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpacingTokens.XS),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = colorScheme.neutralWeak,
                        modifier = Modifier.size(22.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(SpacingTokens.XS))

                    Box(modifier = Modifier.weight(1f)) {
                        when {
                            !isFocused && value.isEmpty() -> {
                                Text(
                                    text = placeholder,
                                    style = TypographyTokens.Subheading2,
                                    color = colorScheme.neutralWeak,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            else -> {
                                innerTextField()
                            }
                        }
                    }
                }

                if (value.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(SpacingTokens.XS))
                    Icon(
                        imageVector = Icons.Outlined.Clear,
                        contentDescription = "Clear",
                        tint = colorScheme.neutralWeak,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable {
                                onClear?.invoke() ?: onValueChange("")
                            }
                    )
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun UIKitSearchFieldPreview() {
    UIKitTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingTokens.M),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            var searchText1 by remember { mutableStateOf("") }
            UIKitSearchField(
                value = searchText1,
                onValueChange = { searchText1 = it },
                placeholder = "Search users..."
            )

            var searchText2 by remember { mutableStateOf("Sample search query") }
            UIKitSearchField(
                value = searchText2,
                onValueChange = { searchText2 = it },
                placeholder = "Search events..."
            )

            var searchText3 by remember { mutableStateOf("") }
            UIKitSearchField(
                value = searchText3,
                onValueChange = { searchText3 = it },
                placeholder = "Custom placeholder",
                onFocusChange = { focused ->
                    // Handle focus change
                }
            )
        }
    }
}