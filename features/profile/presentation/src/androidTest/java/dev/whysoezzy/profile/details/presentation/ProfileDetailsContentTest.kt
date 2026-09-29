package dev.whysoezzy.profile.details.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whysoezzy.uikit.models.UIKitCommunityInfo
import dev.whysoezzy.uikit.theme.UIKitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileDetailsContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun selfAndOtherProfilesRenderCommunitiesReadOnlyWithNavigation() {
        var isOwnProfile by mutableStateOf(true)
        var clickedCommunityId: Long? = null
        composeTestRule.setContent {
            UIKitTheme {
                ProfileContent(
                    uiState = profileState(isOwnProfile = isOwnProfile),
                    onMeetingClick = {},
                    onCommunityClick = { clickedCommunityId = it },
                    onSocialMediaClick = {},
                    onLogoutClick = {},
                )
            }
        }

        assertReadOnlyCommunities("Self")
        composeTestRule.onNodeWithContentDescription("Self subscribed").performClick()
        assertEquals(101L, clickedCommunityId)

        isOwnProfile = false
        clickedCommunityId = null
        composeTestRule.waitForIdle()
        assertReadOnlyCommunities("Other")
        composeTestRule.onNodeWithContentDescription("Other unsubscribed").performClick()
        assertEquals(102L, clickedCommunityId)
    }

    @Test
    fun selfEmptyStateRemainsVisibleAndOtherEmptyStateOmitsCommunitySection() {
        var isOwnProfile by mutableStateOf(true)
        composeTestRule.setContent {
            UIKitTheme {
                ProfileContent(
                    uiState = profileState(isOwnProfile = isOwnProfile, communities = emptyList()),
                    onMeetingClick = {},
                    onCommunityClick = {},
                    onSocialMediaClick = {},
                    onLogoutClick = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText("Вы пока не состоите ни в одном сообществе")
            .assertExists()
        composeTestRule.onNodeWithText("Мои сообщества").assertExists()
        isOwnProfile = false
        composeTestRule.waitForIdle()
        listOf(
            "Вы пока не состоите ни в одном сообществе",
            "Сообщества",
        ).forEach { text ->
            composeTestRule.onAllNodes(hasText(text), useUnmergedTree = false).assertCountEquals(0)
            composeTestRule.onAllNodes(hasText(text), useUnmergedTree = true).assertCountEquals(0)
        }
    }

    private fun assertReadOnlyCommunities(prefix: String) {
        val content = composeTestRule.onNodeWithTag(PROFILE_DETAILS_CONTENT_TAG, useUnmergedTree = true)
        listOf("$prefix subscribed", "$prefix unsubscribed").forEach { title ->
            content.performScrollToNode(hasContentDescription(title, substring = true))
            composeTestRule.onNodeWithContentDescription(title).assertExists()
            composeTestRule.onNodeWithText(title).assertExists()
        }
        listOf("Subscribe", "Unsubscribe").forEach { description ->
            composeTestRule
                .onAllNodes(
                    hasContentDescription(description),
                    useUnmergedTree = false,
                ).assertCountEquals(0)
            composeTestRule
                .onAllNodes(
                    hasContentDescription(description),
                    useUnmergedTree = true,
                ).assertCountEquals(0)
        }
    }

    private fun profileState(
        isOwnProfile: Boolean,
        communities: List<UIKitCommunityInfo> = listOf(
            UIKitCommunityInfo(101, "${if (isOwnProfile) "Self" else "Other"} subscribed", "", true),
            UIKitCommunityInfo(102, "${if (isOwnProfile) "Self" else "Other"} unsubscribed", "", false),
        ),
    ): ProfileDetailsUiState.Success =
        ProfileDetailsUiState.Success(
            userId = 1,
            name = "Test",
            surname = "Profile",
            email = "test@example.com",
            description = "Description",
            avatarUrl = null,
            isOwnProfile = isOwnProfile,
            socialMedias = emptyList(),
            userMeetings = emptyList(),
            userCommunities = communities,
        )
}
