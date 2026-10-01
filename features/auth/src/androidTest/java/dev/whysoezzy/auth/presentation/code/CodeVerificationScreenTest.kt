package dev.whysoezzy.auth.presentation.code

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whysoezzy.uikit.theme.UIKitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CodeVerificationScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun pendingFocusRequestsFocusAndImeOnce() {
        val keyboard = RecordingKeyboardController()
        val focusRequester = FocusRequester()
        var state by mutableStateOf(
            CodeVerificationUiState(
                inputFocusRequestPending = true,
            ),
        )
        var acknowledgementCount = 0

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalSoftwareKeyboardController provides keyboard,
            ) {
                UIKitTheme {
                    CodeVerificationContent(
                        maskedEmail = "p***@example.com",
                        uiState = state,
                        modifier = Modifier.testTag("screen"),
                        focusRequester = focusRequester,
                        onAcknowledgeInputFocusRequest = { acknowledgementCount++ },
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag("UIKitCodeInput.Input", useUnmergedTree = true)
            .assertIsFocused()
        assertEquals(1, keyboard.showCount)
        assertEquals(1, acknowledgementCount)

        state = state.copy(remainingTime = 59)
        composeTestRule.waitForIdle()

        assertEquals(1, keyboard.showCount)
        assertEquals(1, acknowledgementCount)
    }

    private class RecordingKeyboardController : SoftwareKeyboardController {
        var showCount = 0

        override fun show() {
            showCount++
        }

        override fun hide() = Unit
    }
}
