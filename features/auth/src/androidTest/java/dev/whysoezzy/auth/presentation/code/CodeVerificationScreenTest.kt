package dev.whysoezzy.auth.presentation.code

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whysoezzy.auth.domain.models.AuthFailure
import dev.whysoezzy.uikit.theme.UIKitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        var state by mutableStateOf(CodeVerificationUiState(inputFocusRequestPending = true))
        var acknowledgementCount = 0

        composeTestRule.setContent {
            TestContent(
                state = state,
                keyboard = keyboard,
                focusRequester = focusRequester,
                onAcknowledge = {
                    acknowledgementCount++
                    state = state.copy(inputFocusRequestPending = false)
                },
            )
        }

        composeTestRule.waitForIdle()
        assertInputFocused()
        assertEquals(1, keyboard.showCount)
        assertEquals(1, acknowledgementCount)

        state = state.copy(remainingTime = 59)
        composeTestRule.waitForIdle()

        assertEquals(1, keyboard.showCount)
        assertEquals(1, acknowledgementCount)
    }

    @Test
    fun dismissedFocusDoesNotReopenAfterRecomposition() {
        val keyboard = RecordingKeyboardController()
        val dismissalFocusRequester = FocusRequester()
        var state by mutableStateOf(CodeVerificationUiState(inputFocusRequestPending = true))

        composeTestRule.setContent {
            TestContent(
                state = state,
                keyboard = keyboard,
                onAcknowledge = { state = state.copy(inputFocusRequestPending = false) },
                dismissalFocusRequester = dismissalFocusRequester,
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            check(!state.inputFocusRequestPending)
            dismissalFocusRequester.requestFocus()
            keyboard.hide()
            state = state.copy(remainingTime = 58)
        }
        composeTestRule.waitForIdle()

        assertInputNotFocused()
        assertEquals(1, keyboard.showCount)
        assertEquals(1, keyboard.hideCount)
    }

    @Test
    fun pendingFocusWaitsForResumeAndDisposalCancelsIt() {
        val keyboard = RecordingKeyboardController()
        val lifecycleOwner = ControlledLifecycleOwner()
        composeTestRule.runOnUiThread {
            lifecycleOwner.moveTo(Lifecycle.State.CREATED)
        }
        var acknowledgementCount = 0
        var useDisposalOwner by mutableStateOf(false)
        var disposalMounted by mutableStateOf(true)
        val state = CodeVerificationUiState(inputFocusRequestPending = true)
        val disposalOwner = ControlledLifecycleOwner()
        composeTestRule.runOnUiThread {
            disposalOwner.moveTo(Lifecycle.State.CREATED)
        }
        val disposalKeyboard = RecordingKeyboardController()
        var disposalAcknowledgementCount = 0

        composeTestRule.setContent {
            val owner = if (useDisposalOwner) disposalOwner else lifecycleOwner
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (!useDisposalOwner || disposalMounted) {
                    key(useDisposalOwner) {
                        TestContent(
                            state = state,
                            keyboard = if (useDisposalOwner) disposalKeyboard else keyboard,
                            onAcknowledge = {
                                if (useDisposalOwner) {
                                    disposalAcknowledgementCount++
                                } else {
                                    acknowledgementCount++
                                }
                            },
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        assertEquals(0, keyboard.showCount)
        assertEquals(0, acknowledgementCount)

        composeTestRule.runOnUiThread {
            lifecycleOwner.moveTo(Lifecycle.State.RESUMED)
        }
        composeTestRule.waitForIdle()
        assertEquals(1, keyboard.showCount)
        assertEquals(1, acknowledgementCount)

        useDisposalOwner = true
        composeTestRule.waitForIdle()
        disposalMounted = false
        composeTestRule.waitForIdle()
        composeTestRule.runOnUiThread {
            disposalOwner.moveTo(Lifecycle.State.RESUMED)
        }
        composeTestRule.waitForIdle()

        assertEquals(0, disposalKeyboard.showCount)
        assertEquals(0, disposalAcknowledgementCount)
    }

    @Test
    fun consumedFocusRequestSurvivesRecreationWithoutRepeating() {
        val keyboard = RecordingKeyboardController()
        val focusRequester = FocusRequester()
        var state by mutableStateOf(CodeVerificationUiState(inputFocusRequestPending = true))
        var mounted by mutableStateOf(true)
        var acknowledgementCount = 0

        composeTestRule.setContent {
            if (mounted) {
                TestContent(
                    state = state,
                    keyboard = keyboard,
                    focusRequester = focusRequester,
                    onAcknowledge = {
                        acknowledgementCount++
                        state = state.copy(inputFocusRequestPending = false)
                    },
                )
            }
        }

        composeTestRule.waitForIdle()
        mounted = false
        composeTestRule.waitForIdle()
        mounted = true
        composeTestRule.waitForIdle()

        assertEquals(1, keyboard.showCount)
        assertEquals(1, acknowledgementCount)
    }

    @Test
    fun confirmedResendResetRequestsFirstCellAndKeepsResendSemantics() {
        val keyboard = RecordingKeyboardController()
        val focusRequester = FocusRequester()
        var state by mutableStateOf(
            CodeVerificationUiState(
                code = "123456",
                canResend = true,
            ),
        )
        var resendCount = 0
        var acknowledgementCount = 0

        composeTestRule.setContent {
            TestContent(
                state = state,
                keyboard = keyboard,
                focusRequester = focusRequester,
                onResend = {
                    resendCount++
                    state = state.copy(code = "", inputFocusRequestPending = true)
                },
                onAcknowledge = {
                    acknowledgementCount++
                    state = state.copy(inputFocusRequestPending = false)
                },
            )
        }

        composeTestRule
            .onNodeWithText("Отправить код повторно", useUnmergedTree = true)
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, resendCount)
        assertEquals("", state.code)
        assertEquals(1, keyboard.showCount)
        assertEquals(1, acknowledgementCount)
        assertInputFocused()

        state = state.copy(canResend = false, remainingTime = 45)
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Отправить повторно через 45 сек", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun loadingErrorAndConfirmPresentationSemanticsAreObservable() {
        val keyboard = RecordingKeyboardController()
        var state by mutableStateOf(
            CodeVerificationUiState(
                code = "123456",
                isLoading = true,
            ),
        )

        composeTestRule.setContent {
            TestContent(state = state, keyboard = keyboard)
        }

        composeTestRule
            .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate), useUnmergedTree = true)
            .assertIsDisplayed()
        assertFalse(
            composeTestRule
                .onAllNodes(hasText("Подтвердить"), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )

        state = state.copy(
            isLoading = false,
            error = AuthFailure.InvalidCode,
        )
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(
                "The code is incorrect or expired. Request a new code",
                useUnmergedTree = true,
            ).assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("UIKitCodeInput.Input", useUnmergedTree = true)
            .assertTextEquals("123456")
    }

    private fun assertInputFocused() {
        composeTestRule
            .onNodeWithTag("UIKitCodeInput.Input", useUnmergedTree = true)
            .assertIsFocused()
    }

    private fun assertInputNotFocused() {
        composeTestRule
            .onNodeWithTag("UIKitCodeInput.Input", useUnmergedTree = true)
            .assertIsNotFocused()
    }

    @Composable
    private fun TestContent(
        state: CodeVerificationUiState,
        keyboard: RecordingKeyboardController,
        modifier: Modifier = Modifier.testTag("screen"),
        focusRequester: FocusRequester = remember { FocusRequester() },
        onAcknowledge: () -> Unit = {},
        onResend: () -> Unit = {},
        dismissalFocusRequester: FocusRequester? = null,
    ) {
        CompositionLocalProvider(
            LocalSoftwareKeyboardController provides keyboard,
        ) {
            UIKitTheme {
                Box {
                    CodeVerificationContent(
                        maskedEmail = "p***@example.com",
                        uiState = state,
                        modifier = modifier,
                        focusRequester = focusRequester,
                        onResendClick = onResend,
                        onAcknowledgeInputFocusRequest = onAcknowledge,
                    )
                    dismissalFocusRequester?.let { requester ->
                        Box(
                            Modifier
                                .size(1.dp)
                                .focusRequester(requester)
                                .focusable(),
                        )
                    }
                }
            }
        }
    }

    private class ControlledLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle = registry

        fun moveTo(state: Lifecycle.State) {
            registry.currentState = state
        }
    }

    private class RecordingKeyboardController : SoftwareKeyboardController {
        var showCount = 0
        var hideCount = 0

        override fun show() {
            showCount++
        }

        override fun hide() {
            hideCount++
        }
    }
}
