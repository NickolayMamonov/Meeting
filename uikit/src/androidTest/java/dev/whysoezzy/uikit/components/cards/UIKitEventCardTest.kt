package dev.whysoezzy.uikit.components.cards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whysoezzy.uikit.components.tags.UIKitTag
import dev.whysoezzy.uikit.components.tags.UIKitTagSize
import dev.whysoezzy.uikit.models.UIKitAddress
import dev.whysoezzy.uikit.theme.UIKitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

    private val longTags =
        listOf(
            UIKitEventCardTag("Long first tag that needs an ellipsis", true, true),
            UIKitEventCardTag("Design", false, false),
            UIKitEventCardTag("Kotlin", true, false),
            UIKitEventCardTag("Android", false, true),
            UIKitEventCardTag("Duplicate", false, true),
            UIKitEventCardTag("Duplicate", false, true),
        )

    private val duplicateAndBlankTags =
        listOf(
            UIKitEventCardTag("", isSelected = true, isEnabled = false),
            UIKitEventCardTag("Duplicate", isSelected = false, isEnabled = false),
            UIKitEventCardTag("Duplicate", isSelected = false, isEnabled = true),
        )

    private val longCardLabel =
        "Встреча: A long event title that wraps without clipping. Дата: 10 August. " +
            "Адрес: A very long address that should remain contained. Теги: " +
            "Long first tag that needs an ellipsis, Design, Kotlin, Android, Duplicate, Duplicate."

    @Composable
    private fun Card(
        modifier: Modifier = Modifier,
        fontScale: Float = 1f,
        cardType: UIKitEventCardType = UIKitEventCardType.COMPACT,
        title: String = "A long event title that wraps without clipping",
        tags: List<UIKitEventCardTag> = longTags,
        imageUrl: String = "",
        onClick: (() -> Unit)? = null,
    ) {
        CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
            UIKitTheme {
                UIKitEventCard(
                    imageUrl = imageUrl,
                    title = title,
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

    @Test
    fun preferredBoundsHoldAtAllSupportedFontScalesWithoutRepeatedSetContent() {
        composeTestRule.setContent {
            Column {
                listOf(1f, 1.5f, 2f).forEach { fontScale ->
                    Card(
                        modifier = Modifier.testTag("compact-$fontScale"),
                        fontScale = fontScale,
                    )
                    Card(
                        modifier = Modifier.testTag("wide-$fontScale"),
                        fontScale = fontScale,
                        cardType = UIKitEventCardType.WIDE,
                    )
                }
            }
        }

        listOf(1f, 1.5f, 2f).forEach { fontScale ->
            composeTestRule
                .onNodeWithTag("compact-$fontScale", useUnmergedTree = true)
                .assertWidthIsEqualTo(212.dp)
                .assertHeightIsEqualTo(260.dp)
                .assertIsDisplayed()
            composeTestRule
                .onNodeWithTag("wide-$fontScale", useUnmergedTree = true)
                .assertWidthIsEqualTo(320.dp)
                .assertHeightIsEqualTo(280.dp)
                .assertIsDisplayed()
        }
    }

    @Test
    fun tighterAndExactLargerCallerConstraintsWin() {
        composeTestRule.setContent {
            Column {
                Card(
                    modifier = Modifier.width(180.dp).height(120.dp).testTag("tight"),
                    fontScale = 2f,
                )
                Card(
                    modifier = Modifier.width(360.dp).testTag("larger"),
                )
            }
        }

        composeTestRule
            .onNodeWithTag("tight", useUnmergedTree = true)
            .assertWidthIsEqualTo(180.dp)
            .assertHeightIsEqualTo(120.dp)
        composeTestRule
            .onNodeWithTag("larger", useUnmergedTree = true)
            .assertWidthIsEqualTo(360.dp)
            .assertHeightIsEqualTo(260.dp)
    }

    @Test
    fun boundedRowCoversIndicatorOnlyOmittedDigitBoundaryAndContainment() {
        val veryLongTags = listOf(UIKitEventCardTag("A very long tag", false, true), UIKitEventCardTag("Another long tag", false, true))
        composeTestRule.setContent {
            UIKitTheme {
                Column {
                    BoundedEventTagRow(
                        tags = veryLongTags,
                        modifier = Modifier.width(30.dp).testTag("indicator-only"),
                    )
                    BoundedEventTagRow(
                        tags = veryLongTags,
                        modifier = Modifier.width(1.dp).testTag("omitted"),
                    )
                    BoundedEventTagRow(
                        tags = List(9) { UIKitEventCardTag("Long", false, true) },
                        modifier = Modifier.width(35.dp).testTag("nine"),
                    )
                    BoundedEventTagRow(
                        tags = List(10) { UIKitEventCardTag("Long", false, true) },
                        modifier = Modifier.width(35.dp).testTag("ten"),
                    )
                    BoundedEventTagRow(
                        tags =
                            listOf(
                                UIKitEventCardTag("A", true, false),
                                UIKitEventCardTag("Long final tag", false, false),
                                UIKitEventCardTag("Duplicate", false, true),
                            ),
                        modifier = Modifier.width(100.dp).testTag("constrained"),
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("indicator-only", useUnmergedTree = true).assertHeightIsAtLeast(22.dp)
        composeTestRule.onNodeWithTag("omitted", useUnmergedTree = true).assertHeightIsEqualTo(0.dp)
        composeTestRule
            .onAllNodesWithText("+9", useUnmergedTree = true)
            .assertCountEquals(2)
        composeTestRule.onNodeWithText("+10", useUnmergedTree = true).assertExists()
    }

    @Test
    fun boundedRowUsesSixDpGapBetweenRuntimeTagSlots() {
        composeTestRule.setContent {
            UIKitTheme {
                BoundedEventTagRow(
                    tags =
                        listOf(
                            UIKitEventCardTag("A", false, true),
                            UIKitEventCardTag("B", false, true),
                        ),
                    modifier = Modifier.width(200.dp).testTag("gap-row"),
                )
            }
        }

        val first = composeTestRule.onNodeWithText("A", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val second = composeTestRule.onNodeWithText("B", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val tagHorizontalPadding = with(composeTestRule.density) { 4.dp.roundToPx() }
        val actualGap = second.left - first.right - 2f * tagHorizontalPadding
        val expectedGap = with(composeTestRule.density) { 6.dp.toPx() }

        assertEquals(expectedGap, actualGap, 1f)
    }

    @Test
    fun constrainedFinalChipAndSelectedDisabledDuplicateBlankTagsStayContained() {
        composeTestRule.setContent {
            UIKitTheme {
                BoundedEventTagRow(
                    tags =
                        listOf(
                            UIKitEventCardTag("A very long tag", true, false),
                            UIKitEventCardTag("Long final tag", false, false),
                            UIKitEventCardTag("Duplicate", false, true),
                            UIKitEventCardTag("Duplicate", false, true),
                            UIKitEventCardTag("", false, true),
                        ),
                    modifier = Modifier.width(100.dp).testTag("row"),
                )
            }
        }

        val row = composeTestRule.onNodeWithTag("row", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val indicator =
            composeTestRule.onNodeWithText("+4", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(row.contains(indicator.topLeft))
        assertTrue(row.contains(indicator.bottomRight))
        composeTestRule
            .onAllNodesWithText("Duplicate", useUnmergedTree = true)
            .assertCountEquals(2)
        composeTestRule.onNodeWithText("Long final tag", useUnmergedTree = true).assertExists()
    }

    @Test
    fun publicMergedSemanticsContainsOneCompleteLabelAndExactActionCardinality() {
        var clicks = 0
        composeTestRule.setContent {
            UIKitTheme {
                Column {
                    Card(
                        modifier = Modifier.testTag("clickable"),
                        onClick = { clicks++ },
                    )
                    Card(
                        modifier = Modifier.testTag("not-clickable"),
                        title = "Blank and duplicate tags.",
                        tags = duplicateAndBlankTags,
                    )
                }
            }
        }

        val clickable =
            composeTestRule
                .onNodeWithContentDescription(longCardLabel)
                .assertHasClickAction()
                .fetchSemanticsNode()
        assertEquals(listOf(longCardLabel), clickable.config.getOrNull(SemanticsProperties.ContentDescription))
        assertNotNull(clickable.config.getOrNull(SemanticsActions.OnClick))
        composeTestRule.onNodeWithContentDescription(longCardLabel).performClick()
        assertEquals(1, clicks)

        val notClickable =
            composeTestRule
                .onNodeWithContentDescription("Blank and duplicate tags.", substring = true)
                .assertHasNoClickAction()
                .fetchSemanticsNode()
        val notClickableLabel =
            notClickable.config
                .getOrNull(SemanticsProperties.ContentDescription)
                .orEmpty()
                .single()
        assertTrue(notClickableLabel.contains("Blank and duplicate tags."))
        assertTrue(notClickableLabel.contains("10 August"))
        assertTrue(notClickableLabel.contains(address.address))
        assertTrue(notClickableLabel.contains("Duplicate, Duplicate"))
        assertEquals(null, notClickable.config.getOrNull(SemanticsActions.OnClick))
    }

    @Test
    fun physicalBodyRealChipAndIndicatorTapsEachInvokeTheOuterCardOnce() {
        var clicks = 0
        composeTestRule.setContent {
            UIKitTheme {
                Card(
                    modifier = Modifier.testTag("physical-card"),
                    onClick = { clicks++ },
                )
            }
        }

        composeTestRule.onNodeWithTag("physical-card", useUnmergedTree = true).performTouchInput {
            click(Offset(12f, 12f))
            click(Offset(12f, 238f))
            click(Offset(200f, 238f))
        }
        assertEquals(3, clicks)
    }

    @Test
    fun imageFallbackAndImageYieldingKeepWholeBodyInsideTightCards() {
        composeTestRule.setContent {
            UIKitTheme {
                Column {
                    Card(
                        modifier = Modifier.height(100.dp).testTag("yielding"),
                        imageUrl = "not-a-valid-coil-url",
                    )
                    Card(
                        modifier = Modifier.height(98.dp).testTag("row-boundary"),
                        title = "Row boundary",
                        imageUrl = "not-a-valid-coil-url",
                    )
                    Card(
                        modifier = Modifier.height(17.dp).testTag("title-omitted"),
                        title = "Title omitted",
                        imageUrl = "not-a-valid-coil-url",
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("yielding", useUnmergedTree = true).assertHeightIsEqualTo(100.dp)
        composeTestRule.onNodeWithTag("row-boundary", useUnmergedTree = true).assertHeightIsEqualTo(98.dp)
        composeTestRule.onNodeWithTag("title-omitted", useUnmergedTree = true).assertHeightIsEqualTo(17.dp)
        composeTestRule.onNodeWithContentDescription(longCardLabel).assertExists()
    }

    @Test
    fun directMeasuredLayoutAndSmallTagFixtureUseSingleComposition() {
        composeTestRule.setContent {
            UIKitTheme {
                Column {
                    MeasuredEventCardLayout(
                        imageUrl = "not-a-valid-coil-url",
                        title = "Direct internal layout fixture",
                        date = "10 August",
                        address = address,
                        tags = longTags,
                        metrics = EventCardMetrics.forType(UIKitEventCardType.COMPACT),
                        modifier = Modifier.testTag("internal-shell"),
                    )
                    UIKitTag(
                        text = "A long role",
                        size = UIKitTagSize.SMALL,
                        modifier = Modifier.width(64.dp).testTag("small-tag"),
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithTag("internal-shell", useUnmergedTree = true)
            .assertWidthIsEqualTo(212.dp)
            .assertHeightIsEqualTo(260.dp)
        composeTestRule.onNodeWithTag("small-tag", useUnmergedTree = true).assertHeightIsAtLeast(22.dp)
    }
}
