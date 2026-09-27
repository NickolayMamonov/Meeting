package dev.whysoezzy.meetings.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.whysoezzy.features_meetings.R
import dev.whysoezzy.uikit.models.UIKitAdBlock
import dev.whysoezzy.uikit.models.UIKitCommunityInfo
import dev.whysoezzy.uikit.models.UIKitMeetingInfo
import dev.whysoezzy.uikit.theme.UIKitTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreenContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun homeSearchAndClearKeepContentProjectionAndNavigation() {
        val homeMeetings = (1L..9L).map { meeting(it, "Home meeting $it") }
        val searchMeetings = (1L..9L).map { meeting(it + 100, "Search result $it") }
        val fixtures = fixtures()
        var searchQuery by mutableStateOf("")
        var searchResults by mutableStateOf<List<UIKitMeetingInfo>>(emptyList())
        var clickedMeetingId: Long? = null

        composeTestRule.setContent {
            val pagedMeetings =
                remember { flowOf(PagingData.from(homeMeetings)) }.collectAsLazyPagingItems()
            UIKitTheme {
                MainScreenContent(
                    heroMeetings = listOf(meeting(200, "Hero sentinel")),
                    popularMeetings = listOf(meeting(201, "Upcoming sentinel")),
                    searchResults = searchResults,
                    searchQuery = searchQuery,
                    pagedMeetings = pagedMeetings,
                    communities = listOf(
                        UIKitCommunityInfo(
                            id = 300,
                            title = "Community sentinel",
                            imageUrl = "",
                            isSubscribed = false,
                        ),
                    ),
                    adBlocks = fixtures,
                    onMeetingClick = { clickedMeetingId = it },
                    onCommunityClick = {},
                    onUserProfileClick = {},
                    onCommunitySubscribeClick = { _, _ -> },
                )
            }
        }

        assertHomeContent(homeMeetings, fixtures)

        searchQuery = "kotlin"
        searchResults = searchMeetings
        composeTestRule.waitForIdle()
        val content = composeTestRule.onNodeWithTag(MAIN_SCREEN_CONTENT_TAG, useUnmergedTree = true)
        val homeTitles =
            homeMeetings.map { it.title } +
                listOf("Hero sentinel", "Upcoming sentinel", "Community sentinel")
        searchMeetings.forEach { result ->
            content.performScrollToNode(hasContentDescription(result.title, substring = true))
            composeTestRule
                .onNode(hasContentDescription(result.title, substring = true), useUnmergedTree = true)
                .assertIsDisplayed()
            assertSearchExcludesHomeAndAds(fixtures, homeTitles)
        }

        content.performScrollToNode(hasContentDescription(searchMeetings[4].title, substring = true))
        composeTestRule
            .onNode(hasContentDescription(searchMeetings[4].title, substring = true), useUnmergedTree = true)
            .performClick()
        assertEquals(searchMeetings[4].id, clickedMeetingId)

        searchQuery = "no matches"
        searchResults = emptyList()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(
                context.getString(R.string.meetings_main_search_empty),
                useUnmergedTree = true,
            ).assertIsDisplayed()
        assertSearchExcludesHomeAndAds(fixtures, homeTitles)

        searchQuery = ""
        searchResults = emptyList()
        composeTestRule.waitForIdle()
        assertHomeContent(homeMeetings, fixtures)

        searchQuery = "   "
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag(MAIN_SCREEN_CONTENT_TAG, useUnmergedTree = true)
            .performScrollToNode(
                hasText(context.getString(R.string.meetings_main_section_upcoming)),
            )
        composeTestRule
            .onNodeWithText(
                context.getString(R.string.meetings_main_section_upcoming),
                useUnmergedTree = true,
            ).assertIsDisplayed()
        composeTestRule
            .onAllNodes(hasContentDescription(searchMeetings.first().title, substring = true), useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun nonBlankQueryWithNoResultsShowsOnlyExistingEmptyHint() {
        val fixtures = fixtures()
        var searchQuery by mutableStateOf("missing")
        var searchResults by mutableStateOf<List<UIKitMeetingInfo>>(emptyList())

        composeTestRule.setContent {
            val pagedMeetings =
                remember { flowOf(PagingData.empty<UIKitMeetingInfo>()) }.collectAsLazyPagingItems()
            UIKitTheme {
                MainScreenContent(
                    heroMeetings = listOf(meeting(1, "Hero sentinel")),
                    popularMeetings = listOf(meeting(2, "Upcoming sentinel")),
                    searchResults = searchResults,
                    searchQuery = searchQuery,
                    pagedMeetings = pagedMeetings,
                    communities = listOf(
                        UIKitCommunityInfo(3, "Community sentinel", "", false),
                    ),
                    adBlocks = fixtures,
                    onMeetingClick = {},
                    onCommunityClick = {},
                    onUserProfileClick = {},
                    onCommunitySubscribeClick = { _, _ -> },
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(
                context.getString(R.string.meetings_main_search_empty),
                useUnmergedTree = true,
            ).assertIsDisplayed()
        assertSearchExcludesHomeAndAds(
            adBlocks = fixtures,
            homeTitles = listOf("Hero sentinel", "Upcoming sentinel", "Community sentinel"),
        )

        searchQuery = ""
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag(MAIN_SCREEN_CONTENT_TAG, useUnmergedTree = true)
            .performScrollToNode(
                hasText(context.getString(R.string.meetings_main_section_all)),
            )
        composeTestRule
            .onNodeWithText(
                context.getString(R.string.meetings_main_section_all),
                useUnmergedTree = true,
            ).assertIsDisplayed()
    }

    private fun assertHomeContent(
        homeMeetings: List<UIKitMeetingInfo>,
        adBlocks: List<UIKitAdBlock>,
    ) {
        val content = composeTestRule.onNodeWithTag(MAIN_SCREEN_CONTENT_TAG, useUnmergedTree = true)
        composeTestRule
            .onNode(hasContentDescription("Hero sentinel", substring = true), useUnmergedTree = true)
            .assertIsDisplayed()
        listOf(
            R.string.meetings_main_section_upcoming,
            R.string.meetings_main_section_communities,
            R.string.meetings_main_section_all,
        ).forEach { headingRes ->
            val heading = context.getString(headingRes)
            content.performScrollToNode(hasText(heading))
            composeTestRule.onNodeWithText(heading, useUnmergedTree = true).assertIsDisplayed()
        }

        homeMeetings.forEach { meeting ->
            content.performScrollToNode(hasContentDescription(meeting.title, substring = true))
            composeTestRule
                .onNode(hasContentDescription(meeting.title, substring = true), useUnmergedTree = true)
                .assertIsDisplayed()
        }
        adBlocks.forEach { adBlock ->
            content.performScrollToNode(hasText(adBlock.title, substring = true))
            composeTestRule.onNodeWithText(adBlock.title, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    private fun assertSearchExcludesHomeAndAds(
        adBlocks: List<UIKitAdBlock>,
        homeTitles: List<String>,
    ) {
        composeTestRule
            .onAllNodes(
                hasText(context.getString(R.string.meetings_main_section_upcoming)),
                useUnmergedTree = true,
            ).assertCountEquals(0)
        composeTestRule
            .onAllNodes(
                hasText(context.getString(R.string.meetings_main_section_communities)),
                useUnmergedTree = true,
            ).assertCountEquals(0)
        composeTestRule
            .onAllNodes(
                hasText(context.getString(R.string.meetings_main_section_all)),
                useUnmergedTree = true,
            ).assertCountEquals(0)
        adBlocks.forEach { adBlock ->
            composeTestRule
                .onAllNodes(hasText(adBlock.title), useUnmergedTree = true)
                .assertCountEquals(0)
        }
        homeTitles.forEach { title ->
            composeTestRule
                .onAllNodes(hasContentDescription(title, substring = true), useUnmergedTree = true)
                .assertCountEquals(0)
            composeTestRule
                .onAllNodes(hasText(title), useUnmergedTree = true)
                .assertCountEquals(0)
        }
    }

    private fun fixtures(): List<UIKitAdBlock> =
        listOf(
            UIKitAdBlock.CommunitiesAd(
                id = 401,
                title = "Communities ad sentinel",
                description = "Community recommendations",
                communities = listOf(UIKitCommunityInfo(402, "Ad community", "", false)),
            ),
            UIKitAdBlock.TextAd(
                id = 403,
                title = "Text ad sentinel",
                description = "Sponsored meeting",
            ),
            UIKitAdBlock.PeopleAd(
                id = 404,
                title = "People ad sentinel",
                description = "People to follow",
                users = listOf(
                    UIKitAdBlock.PeopleAd.Person(
                        id = 405,
                        name = "Ad person",
                        avatarUrl = "",
                        role = "Host",
                    ),
                ),
            ),
        )

    private fun meeting(id: Long, title: String): UIKitMeetingInfo =
        UIKitMeetingInfo(
            id = id,
            title = title,
            imageUrl = "",
            date = "28 September 2026",
            address = "Test address",
            latitude = 0.0,
            longitude = 0.0,
            tags = emptyList(),
        )
}
