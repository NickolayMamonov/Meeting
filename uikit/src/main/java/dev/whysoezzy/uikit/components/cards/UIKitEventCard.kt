package dev.whysoezzy.uikit.components.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.whysoezzy.uikit.components.tags.UIKitTag
import dev.whysoezzy.uikit.components.tags.UIKitTagSize
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.BorderRadiusTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens
import dev.whysoezzy.uikit.tokens.TypographyTokens

enum class UIKitEventCardType {
    COMPACT,  // Узкая карточка
    WIDE      // Широкая карточка
}

data class UIKitEventCardTag(
    val text: String,
    val isSelected: Boolean = false
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UIKitEventCard(
    imageUrl: String,
    title: String,
    date: String,
    address: String,
    tags: List<UIKitEventCardTag> = emptyList(),
    cardType: UIKitEventCardType = UIKitEventCardType.COMPACT,
    modifier: Modifier = Modifier,
    onCardClick: (() -> Unit)? = null
) {
    val colorScheme = UIKitTheme.colors

    val cardWidth = when (cardType) {
        UIKitEventCardType.COMPACT -> 212.dp
        UIKitEventCardType.WIDE -> 320.dp
    }

    val cardHeight = when (cardType) {
        UIKitEventCardType.WIDE -> 280.dp // Высота для 2-строчного заголовка
        UIKitEventCardType.COMPACT -> 260.dp
    }

    val imageHeight = when (cardType) {
        UIKitEventCardType.COMPACT -> 148.dp
        UIKitEventCardType.WIDE -> 180.dp
    }


    Column(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .clip(RoundedCornerShape(BorderRadiusTokens.L))
            .then(
                onCardClick?.let {
                    Modifier.clickable { it() }
                } ?: Modifier
            ),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.S)
    ) {
        // Image
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            modifier = Modifier
                .fillMaxWidth()
                .height(imageHeight)
                .clip(RoundedCornerShape(BorderRadiusTokens.L)),
            contentScale = ContentScale.Crop
        )

        // Title
        Text(
            text = title,
            style = TypographyTokens.BodyText1,
            color = colorScheme.neutralBody,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            modifier = modifier.fillMaxWidth()
        ) {
            Text(
                text = date,
                style = TypographyTokens.BodyText2,
                color = colorScheme.neutralWeak,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = " · ",
                style = TypographyTokens.BodyText2,
                color = colorScheme.neutralWeak,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = address,
                style = TypographyTokens.BodyText2,
                color = colorScheme.neutralWeak,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

        }
        // Subtitle
//        Text(
//            text = subtitle,
//            style = TypographyTokens.BodyText2,
//            color = colorScheme.neutralWeak,
//            maxLines = 1,
//            overflow = TextOverflow.Ellipsis,
//        )

        // Tags
        if (tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SpacingTokens.S)
            ) {
                tags.forEach { tag ->
                    UIKitTag(
                        text = tag.text,
                        size = UIKitTagSize.SMALL,
                        selected = tag.isSelected
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UIKitEventCardPreview() {
    UIKitTheme {
        val sampleTags = listOf(
            UIKitEventCardTag("Android"),
            UIKitEventCardTag("Design", isSelected = true),
            UIKitEventCardTag("Kotlin"),
            UIKitEventCardTag("UI/UX")
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingTokens.M),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.L)
        ) {
            // Wide card
            UIKitEventCard(
                imageUrl = "https://picsum.photos/320/180",
                title = "This is a wide card with a very long title that should wrap to two lines",
                date = "10 августа",
                address = "Кожевенная линия, 40",
                tags = sampleTags,
                cardType = UIKitEventCardType.WIDE,
                onCardClick = { /* handle click */ }
            )

            // Compact card
            UIKitEventCard(
                imageUrl = "https://picsum.photos/212/148",
                title = "Compact card title",
                date = "10 августа",
                address = "Кожевенная линия, 40",
                tags = sampleTags.take(2),
                cardType = UIKitEventCardType.COMPACT
            )

            // Card without tags
            UIKitEventCard(
                imageUrl = "https://picsum.photos/212/148",
                title = "Event without tags",
                date = "10 августа",
                address = "Кожевенная линия, 40",
                cardType = UIKitEventCardType.COMPACT
            )
        }
    }
}