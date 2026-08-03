package dev.whysoezzy.uikit.components.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whysoezzy.uikit.R
import dev.whysoezzy.uikit.components.tags.UIKitTagSize
import dev.whysoezzy.uikit.models.UIKitAddress
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.BorderRadiusTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens

enum class UIKitEventCardType {
    COMPACT,
    WIDE,
}

data class UIKitEventCardTag(
    val text: String,
    val isSelected: Boolean,
    val isEnabled: Boolean,
    val size: UIKitTagSize = UIKitTagSize.MEDIUM,
    val modifier: Modifier = Modifier,
    val onClick: (() -> Unit)? = null,
)

@Composable
fun UIKitEventCard(
    imageUrl: String,
    title: String,
    date: String,
    address: UIKitAddress,
    tags: List<UIKitEventCardTag> = emptyList(),
    cardType: UIKitEventCardType = UIKitEventCardType.COMPACT,
    modifier: Modifier = Modifier,
    onCardClick: (() -> Unit)? = null,
) {
    val cardLabel =
        if (tags.isEmpty()) {
            stringResource(
                R.string.uikit_event_card_accessibility_no_tags,
                title,
                date,
                address.address,
            )
        } else {
            stringResource(
                R.string.uikit_event_card_accessibility,
                title,
                date,
                address.address,
                tags.joinToString(separator = ", ") { it.text },
            )
        }
    val cardModifier =
        modifier
            .clip(RoundedCornerShape(BorderRadiusTokens.L))
            .clearAndSetSemantics {
                contentDescription = cardLabel
                if (onCardClick != null) {
                    onClick {
                        onCardClick()
                        true
                    }
                }
            }.then(
                onCardClick?.let { callback ->
                    Modifier.clickable(onClick = callback)
                } ?: Modifier,
            )

    MeasuredEventCardLayout(
        imageUrl = imageUrl,
        title = title,
        date = date,
        address = address,
        tags = tags,
        metrics = EventCardMetrics.forType(cardType),
        modifier = cardModifier,
    )
}

private val previewAddress =
    UIKitAddress(
        address = "Кожевенная линия, 40",
        latitude = 49.3345,
        longitude = 55.1234,
    )

private val previewTags =
    listOf(
        UIKitEventCardTag("Android", isSelected = true, isEnabled = true),
        UIKitEventCardTag("Design systems", isSelected = false, isEnabled = false),
        UIKitEventCardTag("Kotlin", isSelected = true, isEnabled = false),
        UIKitEventCardTag("Long duplicate", isSelected = false, isEnabled = true),
        UIKitEventCardTag("Long duplicate", isSelected = false, isEnabled = true),
    )

@Preview(name = "Event card fixtures", showBackground = true)
@Composable
fun UIKitEventCardPreview() {
    UIKitTheme {
        Column(
            modifier = Modifier.padding(SpacingTokens.M),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.L),
        ) {
            UIKitEventCard(
                imageUrl = "",
                title = "A long event title that remains readable over two lines",
                date = "10 августа",
                address = previewAddress,
                tags = previewTags,
                cardType = UIKitEventCardType.WIDE,
                onCardClick = {},
            )
            UIKitEventCard(
                imageUrl = "",
                title = "One overlong tag",
                date = "11 августа",
                address = previewAddress,
                tags =
                    listOf(
                        UIKitEventCardTag(
                            "A very long tag that must stay inside the card",
                            false,
                            true,
                        ),
                    ),
                cardType = UIKitEventCardType.COMPACT,
            )
            UIKitEventCard(
                imageUrl = "",
                title = "No tags",
                date = "12 августа",
                address = previewAddress,
                tags = emptyList(),
                cardType = UIKitEventCardType.COMPACT,
            )
        }
    }
}

@Preview(name = "Event cards at font scale 2", showBackground = true, fontScale = 2f)
@Composable
fun UIKitEventCardEnlargedPreview() {
    UIKitTheme {
        UIKitEventCard(
            imageUrl = "",
            title = "Enlarged text keeps the complete card content contained",
            date = "13 августа",
            address = previewAddress,
            tags = previewTags,
            cardType = UIKitEventCardType.WIDE,
        )
    }
}

@Preview(name = "Event card narrow parent", showBackground = true, widthDp = 180)
@Composable
fun UIKitEventCardNarrowPreview() {
    UIKitTheme {
        UIKitEventCard(
            imageUrl = "",
            title = "Narrow parent",
            date = "14 августа",
            address = previewAddress,
            tags = previewTags,
            modifier = Modifier.width(180.dp),
        )
    }
}
