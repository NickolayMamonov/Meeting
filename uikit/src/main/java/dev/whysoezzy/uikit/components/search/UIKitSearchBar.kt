package dev.whysoezzy.uikit.components.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.BorderRadiusTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens
import dev.whysoezzy.uikit.tokens.TypographyTokens

@Composable
fun UIKitSearchBar(
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    placeholder: String = "Search",
    onSearch: (String) -> Unit = {}
) {
    val colorScheme = UIKitTheme.colors
    var searchText by rememberSaveable { mutableStateOf("") }
    var isActive by remember { mutableStateOf(false) }

    val bgColor = backgroundColor ?: colorScheme.neutralSecondaryBackground
    val activeContentColor = colorScheme.neutralActive
    val contentColor = colorScheme.neutralDisabled

    BasicTextField(
        value = searchText,
        onValueChange = { newValue ->
            searchText = newValue
            onSearch(newValue)
        },
        singleLine = true,
        textStyle = TypographyTokens.BodyText1.copy(color = colorScheme.neutralBody),
        cursorBrush = SolidColor(colorScheme.brandDefault),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 36.dp)
                    .background(bgColor),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = if (searchText.isEmpty()) contentColor else activeContentColor,
                    modifier = Modifier.padding(end = SpacingTokens.S)
                )
                
                Box(modifier = Modifier.weight(1f)) {
                    if (searchText.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = TypographyTokens.BodyText1,
                            color = contentColor
                        )
                    }
                    innerTextField()
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = SpacingTokens.S)
            .onFocusChanged { focusState ->
                isActive = focusState.isFocused
            }
            .clip(shape = RoundedCornerShape(BorderRadiusTokens.XS))
            .border(
                width = 1.dp,
                color = when {
                    isActive -> colorScheme.neutralLine
                    else -> bgColor
                },
                shape = RoundedCornerShape(BorderRadiusTokens.XS)
            )
    )
}

@Preview(showBackground = true)
@Composable
fun UIKitSearchBarPreview() {
    UIKitTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingTokens.M),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            UIKitSearchBar(
                placeholder = "Search users...",
                onSearch = { query ->
                    // Handle search
                }
            )

            UIKitSearchBar(
                placeholder = "Search events...",
                backgroundColor = UIKitTheme.colors.brandBackground,
                onSearch = { query ->
                    // Handle search
                }
            )
        }
    }
}