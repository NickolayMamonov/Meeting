package dev.whysoezzy.uikit.components.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.BorderRadiusTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens
import dev.whysoezzy.uikit.tokens.TypographyTokens

@Composable
fun UIKitSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    placeholder: String = "Search",
    onProfileClick: (() -> Unit)? = null,
    onCancelClick: (() -> Unit)? = null
) {
    val colorScheme = UIKitTheme.colors
    var isActive by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val bgColor = backgroundColor ?: colorScheme.neutralSecondaryBackground
    val activeContentColor = colorScheme.neutralActive
    val contentColor = colorScheme.neutralDisabled

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = SpacingTokens.M),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.XS)
    ) {
        // Search field
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TypographyTokens.BodyText1.copy(color = colorScheme.neutralBody),
            cursorBrush = SolidColor(colorScheme.brandDefault),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 36.dp)
                        .clip(shape = RoundedCornerShape(BorderRadiusTokens.L))
                        .background(bgColor)
                        .border(
                            width = 1.dp,
                            color = when {
                                isActive -> colorScheme.neutralLine
                                else -> bgColor
                            },
                            shape = RoundedCornerShape(BorderRadiusTokens.L)
                        )
                        .padding(horizontal = SpacingTokens.M, vertical = SpacingTokens.S),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = if (query.isEmpty()) contentColor else activeContentColor,
                        modifier = Modifier.padding(end = SpacingTokens.S)
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = TypographyTokens.BodyText1,
                                color = contentColor
                            )
                        }
                        innerTextField()
                    }

                    Box(
                        modifier = Modifier
                            .size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (query.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = contentColor,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { onQueryChange("") }
                            )
                        }
                    }
                }
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    isActive = focusState.isFocused
                }
        )

        // Profile button
        if (onProfileClick != null && onCancelClick != null) {
            Box(
                modifier = Modifier
                    .width(68.dp)
                    .height(40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (query.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onProfileClick() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "Profile",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                } else {
                    TextButton(
                        onClick = {
                            onQueryChange("")
                            focusManager.clearFocus()
                            onCancelClick()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 1.dp)
                    ) {
                        Text(
                            text = "Отмена",
                            color = UIKitTheme.colors.brandDefault
                        )
                    }
                }
            }

        }
    }
}

@Preview(showBackground = true)
@Composable
fun UIKitSearchBarPreview() {
    UIKitTheme {
        var searchQuery1 by remember { mutableStateOf("") }
        var searchQuery2 by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingTokens.S),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            UIKitSearchBar(
                query = searchQuery1,
                onQueryChange = { searchQuery1 = it },
                placeholder = "Search events...",
                onProfileClick = {},
                onCancelClick = {}
            )

            UIKitSearchBar(
                query = searchQuery2,
                onQueryChange = { searchQuery2 = it },
                placeholder = "Search without profile",
                backgroundColor = UIKitTheme.colors.brandBackground
            )
        }
    }
}