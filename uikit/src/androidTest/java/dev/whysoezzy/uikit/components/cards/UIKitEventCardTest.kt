package dev.whysoezzy.uikit.components.cards

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whysoezzy.uikit.components.tags.UIKitTag
import dev.whysoezzy.uikit.components.tags.UIKitTagSize
import dev.whysoezzy.uikit.models.UIKitAddress
import dev.whysoezzy.uikit.theme.UIKitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UIKitEventCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val address =
        UIKitAddress(
            address = "A very long address that should remain contained",
            latitude = 0.0,
            longitude = 0.0,
        )

    private val tags =
        listOf(
            UIKitEventCardTag("Long first tag that needs an ellipsis", true, true),
            UIKitEventCardTag("Design", false, false),
            UIKitEventCardTag("Kotlin", true, false),
            UIKitEventCardTag("Android", false, true),
            UIKitEventCardTag("Duplicate", false, true),
            UIKitEventCardTag("Duplicate", false, true),
        )

    private fun setCard(
        fontScale: Float,
        onClick: (() -> Unit)? = null,
        cardType: UIKitEventCardType = UIKitEventCardType.COMPACT,
        modifier: Modifier = Modifier,
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                UIKitTheme {
                    UIKitEventCard(
                        imageUrl = "",
                        title = "A long event title that wraps without clipping",
                        date = "10 August",
                        address = address,
                        tags = tags,
                        cardType = cardType,
                        modifier = modifier,
                        onCardClick = onClick,
                    )
                }
            }
        }
    }

    @Test
    fun preferredCompactAndWideBoundsHoldAtSupportedFontScales() {
        listOf(1f, 1.5f, 2f).forEach { fontScale ->
            setCard(fontScale, cardType = UIKitEventCardType.COMPACT)
            composeTestRule
                .onNodeWithContentDescription(
                    "Встреча: A long event title that wraps without clipping. Дата: 10 August. " +
                        "Адрес: A very long address that should remain contained. Теги: " +
                        "Long first tag that needs an ellipsis, Design, Kotlin, Android, Duplicate, Duplicate.",
                ).assertWidthIsEqualTo(212.dp)
                .assertIsDisplayed()

            setCard(fontScale, cardType = UIKitEventCardType.WIDE)
            composeTestRule
                .onNodeWithContentDescription(
                    "Встреча: A long event title that wraps without clipping. Дата: 10 August. " +
                        "Адрес: A very long address that should remain contained. Теги: " +
                        "Long first tag that needs an ellipsis, Design, Kotlin, Android, Duplicate, Duplicate.",
                ).assertWidthIsEqualTo(320.dp)
                .assertIsDisplayed()
        }
    }

    @Test
    fun tighterCallerConstraintsWin() {
        setCard(
            fontScale = 2f,
            modifier = Modifier.width(180.dp).height(120.dp),
        )
        composeTestRule
            .onNodeWithContentDescription(
                "Встреча: A long event title that wraps without clipping. Дата: 10 August. " +
                    "Адрес: A very long address that should remain contained. Теги: " +
                    "Long first tag that needs an ellipsis, Design, Kotlin, Android, Duplicate, Duplicate.",
            ).assertWidthIsEqualTo(180.dp)
            .assertHeightIsEqualTo(120.dp)
    }

    @Test
    fun exactLargerCallerWidthRemainsCompatible() {
        setCard(
            fontScale = 1f,
            modifier = Modifier.width(360.dp),
        )
        composeTestRule
            .onNodeWithContentDescription(
                "Встреча: A long event title that wraps without clipping. Дата: 10 August. " +
                    "Адрес: A very long address that should remain contained. Теги: " +
                    "Long first tag that needs an ellipsis, Design, Kotlin, Android, Duplicate, Duplicate.",
            ).assertWidthIsEqualTo(360.dp)
            .assertHeightIsEqualTo(260.dp)
    }

    @Test
    fun boundedInternalRowUsesCompleteOverflowIndicator() {
        composeTestRule.setContent {
            UIKitTheme {
                BoundedEventTagRow(
                    tags = tags,
                    modifier = Modifier.width(120.dp),
                )
            }
        }

        composeTestRule
            .onNodeWithText("+5", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun publicCardHasOneReplacementActionOrNone() {
        var clicks = 0
        setCard(fontScale = 1f, onClick = { clicks++ })
        val label =
            "Встреча: A long event title that wraps without clipping. Дата: 10 August. " +
                "Адрес: A very long address that should remain contained. Теги: " +
                "Long first tag that needs an ellipsis, Design, Kotlin, Android, Duplicate, Duplicate."
        composeTestRule
            .onNodeWithContentDescription(label)
            .assertHasClickAction()
            .performClick()
        assertEquals(1, clicks)

        setCard(fontScale = 1f, onClick = null)
        composeTestRule
            .onNodeWithContentDescription(label)
            .assertHasNoClickAction()
    }

    @Test
    fun smallTagGrowsWithEnlargedText() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                UIKitTheme {
                    UIKitTag(
                        text = "A long role",
                        size = UIKitTagSize.SMALL,
                        modifier = Modifier.width(64.dp),
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithText("A long role", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun internalMeasuredLayoutKeepsNaturalTextAndTagsInsideShell() {
        composeTestRule.setContent {
            UIKitTheme {
                MeasuredEventCardLayout(
                    imageUrl = "",
                    title = "Direct internal layout fixture",
                    date = "10 August",
                    address = address,
                    tags = tags,
                    metrics = EventCardMetrics.forType(UIKitEventCardType.COMPACT),
                    modifier = Modifier,
                )
            }
        }

        composeTestRule
            .onNodeWithText("Direct internal layout fixture", useUnmergedTree = true)
            .assertExists()
    }
}
