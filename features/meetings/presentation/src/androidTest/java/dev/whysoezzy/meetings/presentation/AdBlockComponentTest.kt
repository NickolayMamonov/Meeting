package dev.whysoezzy.meetings.presentation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whysoezzy.uikit.models.UIKitAdBlock
import dev.whysoezzy.uikit.models.UIKitCommunityInfo
import dev.whysoezzy.uikit.theme.UIKitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdBlockComponentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun communityAdKeepsSubscriptionAndIndependentNavigationCallbacks() {
        var subscribed: Pair<Long, Boolean>? = null
        var navigatedCommunityId: Long? = null
        composeTestRule.setContent {
            UIKitTheme {
                AdBlockComponent(
                    adBlock =
                        UIKitAdBlock.CommunitiesAd(
                            id = 1,
                            title = "Community recommendations",
                            description = "Recommended",
                            communities = listOf(UIKitCommunityInfo(7, "Ad community", "", false)),
                        ),
                    onCommunitySubscribe = { id, isSubscribed ->
                        subscribed = id to isSubscribed
                    },
                    onCommunityClick = { navigatedCommunityId = it },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Subscribe").performClick()
        assertEquals(7L to true, subscribed)
        assertNull(navigatedCommunityId)

        composeTestRule.onNodeWithContentDescription("Ad community").performClick()
        assertEquals(7L, navigatedCommunityId)
    }
}
