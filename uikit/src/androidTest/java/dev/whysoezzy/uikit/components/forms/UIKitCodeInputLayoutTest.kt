package dev.whysoezzy.uikit.components.forms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whysoezzy.uikit.theme.UIKitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UIKitCodeInputLayoutTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun compactRowsUseEqualContainedSquaresAndPreserveSpacingIntent() {
        var fixture by mutableStateOf(Fixture(screenWidth = 320.dp))
        composeTestRule.setContent {
            FixtureContent(fixture)
        }

        listOf(320.dp, 360.dp, 411.dp, 480.dp).forEach { screenWidth ->
            fixture = Fixture(screenWidth = screenWidth)
            composeTestRule.waitForIdle()

            val density = Density(1f)
            val row = composeTestRule.onNodeWithTag(CODE_INPUT_ROW_TAG, useUnmergedTree = true)
            val rowBounds = row.fetchSemanticsNode().boundsInRoot
            val cellBounds =
                (0 until 6).map { index ->
                    composeTestRule
                        .onNodeWithTag("$CODE_INPUT_CELL_TAG_PREFIX$index", useUnmergedTree = true)
                        .fetchSemanticsNode()
                        .boundsInRoot
                }
            val expectedAvailableWidth = with(density) { (screenWidth - 48.dp).toPx() }
            val expectedGap = with(density) { 8.dp.toPx() }
            val expectedCell =
                if (screenWidth == 480.dp) {
                    with(density) { 56.dp.toPx() }
                } else {
                    kotlin.math.floor((expectedAvailableWidth - 5 * expectedGap) / 6f)
                }

            assertEquals(expectedAvailableWidth, rowBounds.width, 0f)
            assertTrue(cellBounds.all { it.width == it.height })
            assertTrue(
                cellBounds.drop(1).zip(cellBounds).all { (current, previous) ->
                    current.top == previous.top
                },
            )
            assertTrue(cellBounds.all { it.left >= rowBounds.left && it.right <= rowBounds.right })
            assertTrue(
                cellBounds.zipWithNext().all { (first, second) ->
                    second.left - first.right == expectedGap
                },
            )
            assertTrue(cellBounds.all { it.width == expectedCell })
            assertEquals(
                rowBounds.left + (rowBounds.width - (6 * expectedCell + 5 * expectedGap)) / 2f,
                cellBounds.first().left,
                1f,
            )
            composeTestRule
                .onNodeWithTag("$CODE_INPUT_CELL_TAG_PREFIX${cellBounds.lastIndex}", useUnmergedTree = true)
                .assertIsDisplayed()
        }
    }

    @Test
    fun fractionalDensityFloorsSharedPixelsWithoutOverflow() {
        var fixture by mutableStateOf(Fixture(screenWidth = 320.dp, density = 2.625f))
        composeTestRule.setContent {
            FixtureContent(fixture)
        }
        composeTestRule.waitForIdle()

        val rowBounds =
            composeTestRule
                .onNodeWithTag(CODE_INPUT_ROW_TAG, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val cells =
            (0 until 6).map { index ->
                composeTestRule
                    .onNodeWithTag("$CODE_INPUT_CELL_TAG_PREFIX$index", useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
            }

        assertEquals(714f, rowBounds.width, 0f)
        assertTrue(cells.all { it.width == 101f && it.height == 101f })
        assertTrue(cells.zipWithNext().all { (first, second) -> second.left - first.right == 21f })
        assertTrue(cells.last().right <= rowBounds.right)
    }

    @Test
    fun fontScaleAndStatesKeepDigitsContainedAndVisualStatesPresent() {
        var fixture by mutableStateOf(Fixture(screenWidth = 320.dp, fontScale = 1f, value = ""))
        composeTestRule.setContent {
            FixtureContent(fixture)
        }

        listOf(1f, 1.3f).forEach { fontScale ->
            listOf("", "12", "123456").forEach { value ->
                fixture = Fixture(screenWidth = 320.dp, fontScale = fontScale, value = value)
                composeTestRule.waitForIdle()
                assertTextLayoutsContained(value)
            }
            fixture = Fixture(screenWidth = 320.dp, fontScale = fontScale, value = "12", isError = true)
            composeTestRule.waitForIdle()
            assertTextLayoutsContained("12")
            (0 until 6).forEach { index ->
                composeTestRule
                    .onNodeWithTag("$CODE_INPUT_CELL_TAG_PREFIX$index", useUnmergedTree = true)
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun oneInputFiltersToSixDigitsAndCellTapsKeepFocusOnIt() {
        var value by mutableStateOf("")
        composeTestRule.setContent {
            UIKitTheme {
                UIKitCodeInput(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        composeTestRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true).assertCountEquals(1)
        composeTestRule
            .onNodeWithTag(CODE_INPUT_INPUT_TAG, useUnmergedTree = true)
            .performTextInput("a1٢2-34567")
        composeTestRule.waitForIdle()
        assertEquals("123456", value)

        composeTestRule
            .onNodeWithTag("${CODE_INPUT_CELL_TAG_PREFIX}0", useUnmergedTree = true)
            .performTouchInput { click() }
        composeTestRule
            .onNodeWithTag(CODE_INPUT_INPUT_TAG, useUnmergedTree = true)
            .assertIsFocused()
        composeTestRule
            .onNodeWithTag("${CODE_INPUT_CELL_TAG_PREFIX}5", useUnmergedTree = true)
            .performTouchInput { click() }
        composeTestRule
            .onNodeWithTag(CODE_INPUT_INPUT_TAG, useUnmergedTree = true)
            .assertIsFocused()
    }

    private fun assertTextLayoutsContained(value: String) {
        value.forEach { digit ->
            val textNode =
                composeTestRule.onNodeWithText(digit.toString(), useUnmergedTree = true)
            val textBounds = textNode.fetchSemanticsNode().boundsInRoot
            val cellIndex = value.indexOf(digit)
            val cellBounds =
                composeTestRule
                    .onNodeWithTag("$CODE_INPUT_CELL_TAG_PREFIX$cellIndex", useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
            assertTrue(cellBounds.contains(textBounds.topLeft))
            assertTrue(cellBounds.contains(textBounds.bottomRight))

            val action =
                textNode
                    .fetchSemanticsNode()
                    .config
                    .getOrNull(SemanticsActions.GetTextLayoutResult)
                    ?.action
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            assertTrue(action?.invoke(layouts) == true)
            assertEquals(1, layouts.size)
            assertTrue(!layouts.single().didOverflowWidth)
            assertTrue(!layouts.single().didOverflowHeight)
        }
    }

    @Composable
    private fun FixtureContent(fixture: Fixture) {
        CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides Density(fixture.density, fixture.fontScale),
        ) {
            UIKitTheme {
                Box(
                    modifier =
                        Modifier
                            .width(fixture.screenWidth)
                            .testTag("screen")
                            .padding(horizontal = 24.dp),
                ) {
                    UIKitCodeInput(
                        value = fixture.value,
                        onValueChange = {},
                        isError = fixture.isError,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    private data class Fixture(
        val screenWidth: Dp,
        val density: Float = 1f,
        val fontScale: Float = 1f,
        val value: String = "",
        val isError: Boolean = false,
    )

    private companion object {
        const val CODE_INPUT_ROW_TAG = "UIKitCodeInput.Row"
        const val CODE_INPUT_CELL_TAG_PREFIX = "UIKitCodeInput.Cell."
        const val CODE_INPUT_INPUT_TAG = "UIKitCodeInput.Input"
    }
}
