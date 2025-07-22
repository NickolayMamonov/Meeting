package dev.whysoezzy.uikit.components.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whysoezzy.uikit.components.text.TextBody2
import dev.whysoezzy.uikit.components.text.TextHeading2
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.BorderRadiusTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens
import dev.whysoezzy.uikit.tokens.TypographyTokens

enum class UIKitTagSize {
    SMALL,
    MEDIUM,
    LARGE
}

@Composable
fun UIKitTag(
    text: String,
    size: UIKitTagSize,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val colorScheme = UIKitTheme.colors

    val backgroundColor = when {
        selected -> colorScheme.brandDefault
        else -> colorScheme.neutralLine
    }

    val textColor = when {
        selected -> colorScheme.neutralWhite
        else -> colorScheme.brandDefault
    }

    val tagModifier = when (size) {
        UIKitTagSize.SMALL -> Modifier.height(22.dp)
        UIKitTagSize.MEDIUM -> Modifier.height(35.dp)
        UIKitTagSize.LARGE -> Modifier.height(46.dp)
    }

    Box(
        modifier = modifier
            .then(tagModifier)
            .clip(RoundedCornerShape(BorderRadiusTokens.S))
            .background(backgroundColor)
            .then(
                if (onClick != null && enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        when (size) {
            UIKitTagSize.LARGE -> {
                androidx.compose.material3.Text(
                    text = text,
                    style = TypographyTokens.Heading2.copy(fontWeight = FontWeight.Medium),
                    color = textColor,
                    modifier = Modifier.padding(
                        horizontal = SpacingTokens.M,
                        vertical = 10.dp
                    )
                )
            }

            UIKitTagSize.MEDIUM -> {
                androidx.compose.material3.Text(
                    text = text,
                    style = TypographyTokens.Subheading2.copy(fontWeight = FontWeight.Medium),
                    color = textColor,
                    modifier = Modifier.padding(SpacingTokens.S)
                )
            }

            UIKitTagSize.SMALL -> {
                androidx.compose.material3.Text(
                    text = text,
                    style = TypographyTokens.BodyText2.copy(fontWeight = FontWeight.Medium),
                    color = textColor,
                    modifier = Modifier.padding(
                        horizontal = 6.dp,
                        vertical = 3.dp
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UIKitTagGroup(
    tags: List<String>,
    selectedTags: Set<String> = emptySet(),
    size: UIKitTagSize = UIKitTagSize.MEDIUM,
    modifier: Modifier = Modifier,
    onTagClick: ((String) -> Unit)? = null
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.S),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.S)
    ) {
        tags.forEach { tag ->
            UIKitTag(
                text = tag,
                size = size,
                selected = selectedTags.contains(tag),
                onClick = onTagClick?.let { { it(tag) } }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(showBackground = true)
@Composable
fun UIKitTagPreview() {
    UIKitTheme {
        var selectedMedium by remember { mutableStateOf(false) }
        var selectedLarge by remember { mutableStateOf(false) }
        var selectedTagsInGroup by remember { mutableStateOf(setOf<String>()) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingTokens.M),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.L)
        ) {
            TextHeading2(text = "Individual Tags")

            // Small tags
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.S),
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.S)
            ) {
                UIKitTag(
                    text = "Small",
                    size = UIKitTagSize.SMALL
                )
                UIKitTag(
                    text = "Selected",
                    size = UIKitTagSize.SMALL,
                    selected = true
                )
            }

            // Medium tags
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.S),
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.S)
            ) {
                UIKitTag(
                    text = "Medium",
                    size = UIKitTagSize.MEDIUM,
                    selected = selectedMedium,
                    onClick = { selectedMedium = !selectedMedium }
                )
                UIKitTag(
                    text = "Selected",
                    size = UIKitTagSize.MEDIUM,
                    selected = true
                )
            }

            // Large tags
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.S),
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.S)
            ) {
                UIKitTag(
                    text = "Large",
                    size = UIKitTagSize.LARGE,
                    selected = selectedLarge,
                    onClick = { selectedLarge = !selectedLarge }
                )
                UIKitTag(
                    text = "Selected",
                    size = UIKitTagSize.LARGE,
                    selected = true
                )
            }

            TextHeading2(text = "Tag Group")

            UIKitTagGroup(
                tags = listOf("Android", "Kotlin", "Compose", "Design", "Backend", "Frontend"),
                selectedTags = selectedTagsInGroup,
                size = UIKitTagSize.MEDIUM,
                onTagClick = { tag ->
                    selectedTagsInGroup = if (selectedTagsInGroup.contains(tag)) {
                        selectedTagsInGroup - tag
                    } else {
                        selectedTagsInGroup + tag
                    }
                }
            )
        }
    }
}