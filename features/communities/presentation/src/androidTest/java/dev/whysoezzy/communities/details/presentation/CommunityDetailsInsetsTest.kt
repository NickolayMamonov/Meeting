package dev.whysoezzy.communities.details.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whysoezzy.common.error.ErrorType
import dev.whysoezzy.uikit.models.UIKitMeetingInfo
import dev.whysoezzy.uikit.models.UIKitMeetingStatus
import dev.whysoezzy.uikit.models.UIKitMeetingTag
import dev.whysoezzy.uikit.models.UIKitPerson
import dev.whysoezzy.uikit.models.UIKitTagState
import dev.whysoezzy.uikit.theme.UIKitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommunityDetailsInsetsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun viewportStaysBetweenTopBarAndBottomInsetAcrossCompactFontMatrix() {
        var configuration by mutableStateOf(ViewportConfiguration(1f, 0.dp))
        val physicalDensity = composeTestRule.density.density

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(physicalDensity, configuration.fontScale),
            ) {
                UIKitTheme {
                    Box(
                        modifier = Modifier
                            .width(320.dp)
                            .fillMaxSize(),
                    ) {
                        key(configuration.fontScale, configuration.bottomInset) {
                            CommunityDetailsLayout(
                                uiState = successState(),
                                onBackPressed = {},
                                onShareClick = {},
                                onSubscribeClick = {},
                                onSubscribersClick = {},
                                onMeetingClick = {},
                                onRetry = {},
                                scaffoldContentWindowInsets = WindowInsets(
                                    0.dp,
                                    24.dp,
                                    0.dp,
                                    configuration.bottomInset,
                                ),
                                topBarWindowInsets = WindowInsets(0.dp, 24.dp, 0.dp, 0.dp),
                            )
                        }
                    }
                }
            }
        }

        listOf(1f, 1.5f).forEach { fontScale ->
            listOf(0.dp, 24.dp, 48.dp).forEach { bottomInset ->
                configuration = ViewportConfiguration(fontScale, bottomInset)
                composeTestRule.waitForIdle()

                val bar = bounds(COMMUNITY_DETAILS_TOP_BAR_TAG)
                val viewport = bounds(COMMUNITY_DETAILS_VIEWPORT_TAG)
                val header = bounds(COMMUNITY_DETAILS_HEADER_TAG)
                val rootBottom = composeTestRule
                    .onRoot()
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .bottom
                val bottomInsetPx = with(composeTestRule.density) { bottomInset.toPx() }

                assertTrue("viewport must have positive dimensions", viewport.height > 0f)
                assertEquals("viewport must begin below the pinned bar", bar.bottom, viewport.top, 1f)
                assertEquals(
                    "viewport must end above the supplied bottom inset",
                    rootBottom - bottomInsetPx,
                    viewport.bottom,
                    1f,
                )
                assertEquals("header must not have duplicate top padding", viewport.top, header.top, 1f)

                val barTopBeforeScroll = bar.top
                val viewportNode =
                    composeTestRule
                        .onNodeWithTag(COMMUNITY_DETAILS_VIEWPORT_TAG, useUnmergedTree = true)
                val scrollPositionBefore = verticalScrollPosition()
                viewportNode.performTouchInput {
                    swipeUp(
                        startY = bottom - 48f,
                        endY = top + 48f,
                        durationMillis = 400,
                    )
                }
                composeTestRule.waitForIdle()

                val barAfterScroll = bounds(COMMUNITY_DETAILS_TOP_BAR_TAG)
                assertTrue(
                    "real gesture must move the details content",
                    verticalScrollPosition() > scrollPositionBefore,
                )
                assertEquals("top bar must remain stationary", barTopBeforeScroll, barAfterScroll.top, 1f)

                viewportNode.performScrollToNode(hasTestTag(COMMUNITY_DETAILS_TERMINAL_TAG))
                val terminal = bounds(COMMUNITY_DETAILS_TERMINAL_TAG)
                val finalViewport = bounds(COMMUNITY_DETAILS_VIEWPORT_TAG)
                assertTrue(
                    "terminal content must remain above the bottom boundary",
                    terminal.bottom <= finalViewport.bottom + 1f,
                )
            }
        }
    }

    @Test
    fun loadingErrorSuccessAndInteractionsKeepExistingCallbacksAndOrder() {
        var state by mutableStateOf<CommunityDetailsUiState>(CommunityDetailsUiState.Loading)
        var backCount = 0
        var shareCount = 0
        var retryCount = 0
        var subscribeCount = 0
        var subscribersCount = 0
        val meetingIds = mutableListOf<Long>()

        composeTestRule.setContent {
            UIKitTheme {
                CommunityDetailsLayout(
                    uiState = state,
                    onBackPressed = { backCount++ },
                    onShareClick = { shareCount++ },
                    onSubscribeClick = {
                        subscribeCount++
                        val success = state as CommunityDetailsUiState.Success
                        state = success.copy(isSubscribed = !success.isSubscribed)
                    },
                    onSubscribersClick = { subscribersCount++ },
                    onMeetingClick = { meetingIds += it },
                    onRetry = { retryCount++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(COMMUNITY_DETAILS_TOP_BAR_TAG).assertExists()
        state = CommunityDetailsUiState.Error(ErrorType.Server)
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Повторить", useUnmergedTree = true)
            .performTouchInput { click() }
        assertEquals(1, retryCount)

        state = successState()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithContentDescription("Synthetic community", useUnmergedTree = true)
            .assertExists()
        composeTestRule.onNodeWithContentDescription("Back", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithContentDescription("Share", useUnmergedTree = true).performClick()
        assertEquals(1, backCount)
        assertEquals(1, shareCount)

        composeTestRule
            .onNodeWithText("Вступить в сообщество", useUnmergedTree = true)
            .performTouchInput { click() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Покинуть сообщество", useUnmergedTree = true)
            .performTouchInput { click() }
        assertEquals(2, subscribeCount)

        val viewport =
            composeTestRule.onNodeWithTag(COMMUNITY_DETAILS_VIEWPORT_TAG, useUnmergedTree = true)
        viewport.performScrollToNode(hasContentDescription("Встреча: Synthetic active", substring = true))
        composeTestRule
            .onNodeWithContentDescription(
                "Встреча: Synthetic active",
                substring = true,
                useUnmergedTree = true,
            ).performClick()
        viewport.performScrollToNode(hasTestTag(COMMUNITY_DETAILS_TERMINAL_TAG))
        composeTestRule
            .onNodeWithContentDescription(
                "Встреча: Synthetic past",
                substring = true,
                useUnmergedTree = true,
            ).performClick()
        assertEquals(listOf(101L, 202L), meetingIds)

        viewport.performScrollToNode(hasText("+2"))
        composeTestRule
            .onNodeWithText("+2", useUnmergedTree = true)
            .performTouchInput { click() }
        assertEquals(1, subscribersCount)

        composeTestRule.onNodeWithText("Встречи", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Прошлые встречи", useUnmergedTree = true).assertExists()
    }

    private fun bounds(tag: String) =
        composeTestRule
            .onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

    private fun verticalScrollPosition(): Float =
        requireNotNull(
            composeTestRule
                .onNodeWithTag(COMMUNITY_DETAILS_VIEWPORT_TAG, useUnmergedTree = true)
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.VerticalScrollAxisRange),
        ).value()

    private fun successState(isSubscribed: Boolean = false): CommunityDetailsUiState.Success =
        CommunityDetailsUiState.Success(
            communityId = 7L,
            imageUrl = "",
            title = "Synthetic community",
            tags = listOf(UIKitMeetingTag(1L, "Synthetic tag", UIKitTagState.ACTIVE)),
            description = "Synthetic description ".repeat(220),
            isSubscribed = isSubscribed,
            subscribersCount = 3,
            subscribers = listOf(UIKitPerson(1L, "Synthetic", "Person", "")),
            activeMeetings = listOf(meeting(101L, "Synthetic active", UIKitMeetingStatus.ACTIVE)),
            pastMeetings = listOf(meeting(202L, "Synthetic past", UIKitMeetingStatus.COMPLETED)),
        )

    private fun meeting(
        id: Long,
        title: String,
        status: UIKitMeetingStatus,
    ) = UIKitMeetingInfo(
        id = id,
        title = title,
        imageUrl = "",
        date = "Synthetic date",
        address = "Synthetic address",
        latitude = 0.0,
        longitude = 0.0,
        tags = emptyList(),
        meetingStatus = status,
    )

    private data class ViewportConfiguration(
        val fontScale: Float,
        val bottomInset: Dp,
    )
}
