package dev.whysoezzy.uikit.components.cards

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whysoezzy.uikit.models.UIKitCommunitySubscriptionAction
import dev.whysoezzy.uikit.theme.UIKitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        composeTestRule.setContent {
            UIKitTheme {
                Column {
                    UIKitCommunityCard(
                        imageUrl = "",
                        title = "Read-only",
                        subscriptionAction = UIKitCommunitySubscriptionAction.ReadOnly,
                        modifier = Modifier.testTag("read-only"),
                    )
                    UIKitCommunityCard(
                        imageUrl = "",
                        title = "Actionable",
                        subscriptionAction = UIKitCommunitySubscriptionAction.Actionable(
                            isSubscribed = false,
                            onSubscribeClick = {},
                        ),
                        modifier = Modifier.testTag("actionable"),
                    )
                }
            }
        }

        val readOnlyNode = composeTestRule.onNodeWithTag("read-only", useUnmergedTree = true)
        val actionableNode = composeTestRule.onNodeWithTag("actionable", useUnmergedTree = true)
        val readOnlyBounds = readOnlyNode.getUnclippedBoundsInRoot()
        val actionableBounds = actionableNode.getUnclippedBoundsInRoot()
        val readOnlyHeight = readOnlyBounds.bottom - readOnlyBounds.top
        val actionableHeight = actionableBounds.bottom - actionableBounds.top
        val omittedHeight = actionableHeight - readOnlyHeight
        assertTrue("omitted control height should include button and gap", omittedHeight >= 40.dp)
        assertTrue("omitted control height should remain bounded", omittedHeight <= 50.dp)
    }
}
