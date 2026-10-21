package dev.whysoezzy.uikit.components.cards

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whysoezzy.uikit.models.UIKitCommunitySubscriptionAction
import dev.whysoezzy.uikit.theme.UIKitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UIKitCommunityCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun readOnlyCardHasNoSubscriptionControlAndKeepsNavigation() {
        var clickedId = 0
        composeTestRule.setContent {
            UIKitTheme {
                UIKitCommunityCard(
                    imageUrl = "",
                    title = "Read-only community",
                    subscriptionAction = UIKitCommunitySubscriptionAction.ReadOnly,
                    modifier = Modifier.testTag("read-only-card"),
                    onCardClick = { clickedId = 42 },
                )
            }
        }

        composeTestRule
            .onAllNodes(hasContentDescription("Subscribe"), useUnmergedTree = true)
            .assertCountEquals(0)
        composeTestRule
            .onAllNodes(hasContentDescription("Unsubscribe"), useUnmergedTree = true)
            .assertCountEquals(0)
        composeTestRule.onNodeWithContentDescription("Read-only community").performClick()
        assertEquals(42, clickedId)
    }

    @Test
    fun actionableStatesInvokeInverseOnceAndDoNotNavigate() {
        val events = mutableListOf<Boolean>()
        var navigationCount = 0
        var selected by mutableStateOf(false)
        composeTestRule.setContent {
            UIKitTheme {
                UIKitCommunityCard(
                    imageUrl = "",
                    title = "Actionable community",
                    subscriptionAction = UIKitCommunitySubscriptionAction.Actionable(
                        isSubscribed = selected,
                        onSubscribeClick = {
                            events += it
                            selected = it
                        },
                    ),
                    onCardClick = { navigationCount++ },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Subscribe").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Unsubscribe").performClick()
        assertEquals(listOf(true, false), events)
        assertEquals(0, navigationCount)
    }

    @Test
    fun recomposingActionableToReadOnlyRemovesStaleSemantics() {
        var readOnly by mutableStateOf(false)
        composeTestRule.setContent {
            UIKitTheme {
                UIKitCommunityCard(
                    imageUrl = "",
                    title = "Recomposed community",
                    subscriptionAction =
                        if (readOnly) {
                            UIKitCommunitySubscriptionAction.ReadOnly
                        } else {
                            UIKitCommunitySubscriptionAction.Actionable(
                                isSubscribed = true,
                                onSubscribeClick = {},
                            )
                        },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Unsubscribe").assertExists()
        readOnly = true
        composeTestRule.waitForIdle()
        composeTestRule
            .onAllNodes(hasContentDescription("Subscribe"), useUnmergedTree = true)
            .assertCountEquals(0)
        composeTestRule
            .onAllNodes(hasContentDescription("Unsubscribe"), useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun readOnlyOmissionRemovesButtonHeightAndGap() {
        val title = "Equivalent community"
        val imageUrl = "equivalent-image"
        var readOnly by mutableStateOf(true)
        composeTestRule.setContent {
            UIKitTheme {
                UIKitCommunityCard(
                    imageUrl = imageUrl,
                    title = title,
                    subscriptionAction =
                        if (readOnly) {
                            UIKitCommunitySubscriptionAction.ReadOnly
                        } else {
                            UIKitCommunitySubscriptionAction.Actionable(
                                isSubscribed = false,
                                onSubscribeClick = {},
                            )
                        },
                    modifier = Modifier.testTag("equivalent-card"),
                )
            }
        }

        val card = composeTestRule.onNodeWithTag("equivalent-card", useUnmergedTree = true)
        val readOnlyBounds = card.getUnclippedBoundsInRoot()
        val readOnlyImageBounds =
            composeTestRule.onNodeWithContentDescription(title).getUnclippedBoundsInRoot()
        val readOnlyTitleBounds =
            composeTestRule.onNodeWithText(title).getUnclippedBoundsInRoot()

        readOnly = false
        composeTestRule.waitForIdle()

        val actionableBounds = card.getUnclippedBoundsInRoot()
        val actionableImageBounds =
            composeTestRule.onNodeWithContentDescription(title).getUnclippedBoundsInRoot()
        val actionableTitleBounds =
            composeTestRule.onNodeWithText(title).getUnclippedBoundsInRoot()
        val readOnlyHeight = readOnlyBounds.bottom - readOnlyBounds.top
        val actionableHeight = actionableBounds.bottom - actionableBounds.top
        val omittedHeight = actionableHeight - readOnlyHeight
        val readOnlyWidth = readOnlyBounds.right - readOnlyBounds.left
        val actionableWidth = actionableBounds.right - actionableBounds.left
        val readOnlyImageWidth = readOnlyImageBounds.right - readOnlyImageBounds.left
        val actionableImageWidth = actionableImageBounds.right - actionableImageBounds.left
        val readOnlyImageHeight = readOnlyImageBounds.bottom - readOnlyImageBounds.top
        val actionableImageHeight = actionableImageBounds.bottom - actionableImageBounds.top
        val readOnlyTitleWidth = readOnlyTitleBounds.right - readOnlyTitleBounds.left
        val actionableTitleWidth = actionableTitleBounds.right - actionableTitleBounds.left
        val readOnlyTitleHeight = readOnlyTitleBounds.bottom - readOnlyTitleBounds.top
        val actionableTitleHeight = actionableTitleBounds.bottom - actionableTitleBounds.top

        assertEquals(104.dp.value, readOnlyWidth.value, 0f)
        assertEquals(readOnlyWidth.value, actionableWidth.value, 0f)
        assertEquals(37.dp.value + 4.dp.value, omittedHeight.value, 1.dp.value)
        assertEquals(readOnlyImageWidth.value, actionableImageWidth.value, 0f)
        assertEquals(readOnlyImageHeight.value, actionableImageHeight.value, 0f)
        assertEquals(readOnlyTitleWidth.value, actionableTitleWidth.value, 0f)
        assertEquals(readOnlyTitleHeight.value, actionableTitleHeight.value, 0f)
    }
}
