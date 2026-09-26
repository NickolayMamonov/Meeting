package dev.whysoezzy.meetings.details.presentation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whysoezzy.uikit.models.UIKitAddress
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.SpacingTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeetingDetailsInsetsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun actionStates_clearNavigationAndInvokeCallbackOncePerInteriorTap() {
        var currentFixture by mutableStateOf(ActionFixture(ActionState(), 0))
        var callbackCount = 0
        composeTestRule.setContent {
            UIKitTheme {
                MeetingDetailsLayout(
                    uiState = fixture(currentFixture.actionState),
                    onBackPressed = {},
                    onShareClick = {},
                    onJoinClick = { callbackCount++ },
                    onLeaveClick = { callbackCount++ },
                    onOpenExternalClick = { callbackCount++ },
                    onHostClick = {},
                    onParticipantClick = {},
                    onCommunityClick = {},
                    onOtherMeetingClick = {},
                    onMapClick = {},
                    onParticipantsClick = {},
                    onRetry = {},
                    navigationBarInsets = WindowInsets(
                        0,
                        0,
                        0,
                        currentFixture.bottomInset,
                    ),
                )
            }
        }

        listOf(
            ActionState(joined = false),
            ActionState(joined = true),
            ActionState(externalUrl = "https://registration.example"),
        ).forEach { actionState ->
            listOf(0, 24, 48).forEach { bottomInset ->
                callbackCount = 0
                currentFixture = ActionFixture(actionState, bottomInset)
                composeTestRule.waitForIdle()

                val action = composeTestRule.onNodeWithTag(MEETING_DETAILS_ACTION_TAG, useUnmergedTree = true)
                val button = composeTestRule.onNodeWithTag(MEETING_DETAILS_CTA_TAG, useUnmergedTree = true)
                action.assertIsDisplayed()
                button.assertIsDisplayed()

                val actionBounds = action.fetchSemanticsNode().boundsInRoot
                val buttonBounds = button.fetchSemanticsNode().boundsInRoot
                val expectedFixedPadding =
                    with(composeTestRule.density) { SpacingTokens.L.toPx() }
                assertEquals(
                    expectedFixedPadding + bottomInset,
                    actionBounds.bottom - buttonBounds.bottom,
                    1f,
                )
                val rootBottom =
                    composeTestRule
                        .onRoot()
                        .fetchSemanticsNode()
                        .boundsInRoot
                        .bottom
                assertTrue(buttonBounds.bottom <= rootBottom - bottomInset + 1f)

                button.performTouchInput {
                    click(center)
                    click(Offset(buttonBounds.width / 2f, buttonBounds.height - 1f))
                }
                assertEquals(2, callbackCount)
            }
        }
    }

    @Test
    fun terminalContentRemainsReachableAboveFixedActionSurface() {
        composeTestRule.setContent {
            UIKitTheme {
                MeetingDetailsLayout(
                    uiState = fixture(ActionState(), descriptionLength = 10_000),
                    onBackPressed = {},
                    onShareClick = {},
                    onJoinClick = {},
                    onLeaveClick = {},
                    onOpenExternalClick = {},
                    onHostClick = {},
                    onParticipantClick = {},
                    onCommunityClick = {},
                    onOtherMeetingClick = {},
                    onMapClick = {},
                    onParticipantsClick = {},
                    onRetry = {},
                    navigationBarInsets = WindowInsets(0, 0, 0, 48),
                )
            }
        }
        composeTestRule.waitForIdle()

        val content = composeTestRule.onNodeWithTag(MEETING_DETAILS_CONTENT_TAG, useUnmergedTree = true)
        content.performScrollToNode(hasTestTag(MEETING_DETAILS_TERMINAL_TAG))
        val terminalBounds =
            composeTestRule
                .onNodeWithTag(MEETING_DETAILS_TERMINAL_TAG, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val actionBounds =
            composeTestRule
                .onNodeWithTag(MEETING_DETAILS_ACTION_TAG, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot

        assertTrue("terminal content overlaps fixed action surface", terminalBounds.bottom <= actionBounds.top)
    }

    private fun fixture(
        actionState: ActionState,
        descriptionLength: Int = 100,
    ): MeetingDetailsUiState.Success =
        MeetingDetailsUiState.Success(
            meetingId = 1L,
            imageUrl = "",
            title = "Compose meeting",
            dateTime = "26 September 2026, 18:00",
            address = UIKitAddress("Test address", 0.0, 0.0),
            tags = emptyList(),
            description = "Description ".repeat(descriptionLength),
            host = null,
            nearestMetro = "",
            participants = emptyList(),
            isUserJoined = actionState.joined,
            totalPlaces = 20,
            community = null,
            otherMeetings = emptyList(),
            externalUrl = actionState.externalUrl,
        )

    private data class ActionState(
        val joined: Boolean = false,
        val externalUrl: String? = null,
    )

    private data class ActionFixture(
        val actionState: ActionState,
        val bottomInset: Int,
    )
}
