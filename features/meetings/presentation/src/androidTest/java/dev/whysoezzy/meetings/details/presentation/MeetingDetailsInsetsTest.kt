package dev.whysoezzy.meetings.details.presentation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whysoezzy.common.error.ErrorType
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
        var currentFixture by mutableStateOf(ActionFixture(ActionState(), 0.dp))
        var joinCallbackCount = 0
        var leaveCallbackCount = 0
        var externalCallbackCount = 0
        composeTestRule.setContent {
            UIKitTheme {
                MeetingDetailsLayout(
                    uiState = fixture(currentFixture.actionState),
                    onBackPressed = {},
                    onShareClick = {},
                    onJoinClick = { joinCallbackCount++ },
                    onLeaveClick = { leaveCallbackCount++ },
                    onOpenExternalClick = { externalCallbackCount++ },
                    onHostClick = {},
                    onParticipantClick = {},
                    onCommunityClick = {},
                    onOtherMeetingClick = {},
                    onMapClick = {},
                    onParticipantsClick = {},
                    onRetry = {},
                    navigationBarInsets = WindowInsets(
                        0.dp,
                        0.dp,
                        0.dp,
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
            listOf(0.dp, 24.dp, 48.dp).forEach { bottomInset ->
                joinCallbackCount = 0
                leaveCallbackCount = 0
                externalCallbackCount = 0
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
                val bottomInsetPx =
                    with(composeTestRule.density) { bottomInset.toPx() }
                assertEquals(
                    expectedFixedPadding + bottomInsetPx,
                    actionBounds.bottom - buttonBounds.bottom,
                    1f,
                )
                val rootBottom =
                    composeTestRule
                        .onRoot()
                        .fetchSemanticsNode()
                        .boundsInRoot
                        .bottom
                assertTrue(buttonBounds.bottom <= rootBottom - bottomInsetPx + 1f)

                button.performTouchInput {
                    click(center)
                }
                assertCallbackCounts(
                    actionState = actionState,
                    joinCount = joinCallbackCount,
                    leaveCount = leaveCallbackCount,
                    externalCount = externalCallbackCount,
                    expectedCount = 1,
                )
                button.performTouchInput {
                    click(Offset(buttonBounds.width / 2f, buttonBounds.height - 1f))
                }
                assertCallbackCounts(
                    actionState = actionState,
                    joinCount = joinCallbackCount,
                    leaveCount = leaveCallbackCount,
                    externalCount = externalCallbackCount,
                    expectedCount = 2,
                )
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
                    navigationBarInsets = WindowInsets(0.dp, 0.dp, 0.dp, 48.dp),
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

    @Test
    fun loadingAndErrorBranchesKeepBodyUsableWithoutActionBar() {
        var state by mutableStateOf<MeetingDetailsUiState>(MeetingDetailsUiState.Loading)
        var retryCount = 0
        var backCount = 0
        composeTestRule.setContent {
            UIKitTheme {
                MeetingDetailsLayout(
                    uiState = state,
                    onBackPressed = { backCount++ },
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
                    onRetry = { retryCount++ },
                    navigationBarInsets = WindowInsets(0.dp, 0.dp, 0.dp, 48.dp),
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag(MEETING_DETAILS_LOADING_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithTag(MEETING_DETAILS_ACTION_TAG, useUnmergedTree = true)
            .assertCountEquals(0)

        state = MeetingDetailsUiState.Error(ErrorType.Server)
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag(MEETING_DETAILS_ERROR_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(MEETING_DETAILS_RETRY_TAG, useUnmergedTree = true)
            .performClick()
        composeTestRule
            .onNodeWithTag(MEETING_DETAILS_BACK_TAG, useUnmergedTree = true)
            .performClick()

        assertEquals(1, retryCount)
        assertEquals(1, backCount)
        composeTestRule
            .onAllNodesWithTag(MEETING_DETAILS_ACTION_TAG, useUnmergedTree = true)
            .assertCountEquals(0)
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
        val bottomInset: Dp,
    )

    private fun assertCallbackCounts(
        actionState: ActionState,
        joinCount: Int,
        leaveCount: Int,
        externalCount: Int,
        expectedCount: Int,
    ) {
        when {
            actionState.externalUrl != null -> {
                assertEquals(0, joinCount)
                assertEquals(0, leaveCount)
                assertEquals(expectedCount, externalCount)
            }

            actionState.joined -> {
                assertEquals(0, joinCount)
                assertEquals(expectedCount, leaveCount)
                assertEquals(0, externalCount)
            }

            else -> {
                assertEquals(expectedCount, joinCount)
                assertEquals(0, leaveCount)
                assertEquals(0, externalCount)
            }
        }
    }
}
