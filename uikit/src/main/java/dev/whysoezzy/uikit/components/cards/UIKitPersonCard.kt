package dev.whysoezzy.uikit.components.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whysoezzy.uikit.components.avatars.UIKitAvatar
import dev.whysoezzy.uikit.components.tags.UIKitTag
import dev.whysoezzy.uikit.components.tags.UIKitTagSize
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.BorderRadiusTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens
import dev.whysoezzy.uikit.tokens.TypographyTokens

@Composable
fun UIKitPersonCard(
    name: String,
    role: String,
    imageUrl: String,
    modifier: Modifier = Modifier,
    isTagSelected: Boolean = false,
    onTagClick: (() -> Unit)? = null,
    onCardClick: (() -> Unit)? = null
) {
    val colorScheme = UIKitTheme.colors

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(BorderRadiusTokens.L))
            .then(
                onCardClick?.let { 
                    Modifier.clickable { it() }
                } ?: Modifier
            )
            .padding(SpacingTokens.M)
            .width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.S)
    ) {
        // Avatar
        UIKitAvatar(
            imageUrl = imageUrl,
            size = 64.dp
        )
        
        // Name
        Text(
            text = name,
            style = TypographyTokens.BodyText1,
            color = colorScheme.neutralBody,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Role Tag
        UIKitTag(
            text = role,
            size = UIKitTagSize.SMALL,
            selected = isTagSelected,
            onClick = onTagClick
        )
    }
}

@Preview(showBackground = true)
@Composable
fun UIKitPersonCardPreview() {
    UIKitTheme {
        var isTagSelected by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingTokens.M),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.L)
        ) {
            // Simple card
            UIKitPersonCard(
                name = "John Doe",
                role = "Designer",
                imageUrl = "https://picsum.photos/200"
            )

            // Card with long name
            UIKitPersonCard(
                name = "John Doe with a very long name",
                role = "UX Designer",
                imageUrl = "https://picsum.photos/201"
            )

            // Interactive card
            UIKitPersonCard(
                name = "Jane Smith",
                role = "Developer",
                imageUrl = "https://picsum.photos/202",
                isTagSelected = isTagSelected,
                onTagClick = { isTagSelected = !isTagSelected },
                onCardClick = { /* handle card click */ }
            )

            // Card without image
            UIKitPersonCard(
                name = "Alex Johnson",
                role = "Manager",
                imageUrl = ""
            )
        }
    }
}