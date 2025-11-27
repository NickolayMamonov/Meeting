package dev.whysoezzy.uikit.components.cards

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.SpacingTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UIKitHostCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun hostCard_displaysTitleWhenProvided() {
        composeTestRule.setContent {
            UIKitTheme {
                UIKitHostCard(
                    title = "Ведущий",
                    name = "Александр",
                    surname = "Петров",
                    description = "Senior Android Developer",
                    imageUrl = "",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SpacingTokens.M)
                )
            }
        }

        composeTestRule.onNodeWithText("Ведущий").assertExists()
        composeTestRule.onNodeWithText("Александр Петров").assertExists()
        composeTestRule.onNodeWithText("Senior Android Developer").assertExists()
    }

    @Test
    fun hostCard_hidesTitleWhenNotProvided() {
        composeTestRule.setContent {
            UIKitTheme {
                UIKitHostCard(
                    name = "Мария",
                    surname = "Иванова",
                    description = "UX Designer",
                    imageUrl = "",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SpacingTokens.M)
                )
            }
        }

        composeTestRule.onNodeWithText("Мария Иванова").assertExists()
        composeTestRule.onNodeWithText("UX Designer").assertExists()
    }
}
