package dev.whysoezzy.auth.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whysoezzy.auth.domain.models.AuthFailure
import dev.whysoezzy.auth.presentation.code.CodeVerificationContent
import dev.whysoezzy.auth.presentation.code.CodeVerificationUiState
import dev.whysoezzy.auth.presentation.email.EmailInputContent
import dev.whysoezzy.auth.presentation.email.EmailInputUiState
import dev.whysoezzy.auth.presentation.name.NameFieldError
import dev.whysoezzy.auth.presentation.name.NameInputContent
import dev.whysoezzy.auth.presentation.name.NameInputUiState
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.SpacingTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val AUTH_FORM_TEST_ROOT_TAG = "auth-form-test-root"

@RunWith(AndroidJUnit4::class)
class AuthFormInsetsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun fittingEmail_keepsActionBottomAlignedAndClearsImeUnion() {
        var bottomInset by mutableStateOf(0.dp)
        setContainer(height = 640.dp) {
            EmailInputContent(
                state = EmailInputUiState(email = "person@example.com"),
                modifier = Modifier.fillMaxSize(),
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, bottomInset),
            )
        }

        val root = rootBounds()
        val action = actionBounds()
        val designPadding = with(composeTestRule.density) { SpacingTokens.L.toPx() }
        assertEquals(root.bottom - designPadding, action.bottom, 1f)

        bottomInset = 220.dp
        composeTestRule.waitForIdle()
        val imeBottom = with(composeTestRule.density) { bottomInset.toPx() }
        assertTrue(actionBounds().bottom <= rootBounds().bottom - imeBottom - designPadding + 1f)
    }

    @Test
    fun exactFit_hasNoScrollMovement() {
        val bodyHeight = 120.dp
        val actionHeight = 48.dp
        val rootHeight = bodyHeight + actionHeight + (SpacingTokens.L * 4)
        setContainer(height = rootHeight) {
            AuthFormLayout(
                modifier = Modifier.fillMaxSize(),
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                body = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(bodyHeight),
                    )
                },
                action = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(actionHeight),
                    )
                },
            )
        }

        val before = actionBounds()
        composeTestRule
            .onNodeWithTag(AUTH_FORM_SCROLL_TAG, useUnmergedTree = true)
            .performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()

        assertEquals(before.bottom, actionBounds().bottom, 1f)
    }

    @Test
    fun overflowingEmail_scrollsWholeFormAndRetainsEditableTextAfterResize() {
        var email by mutableStateOf("")
        var bottomInset by mutableStateOf(0.dp)
        setContainer(height = 280.dp) {
            EmailInputContent(
                state = EmailInputUiState(email = email, error = AuthFailure.InvalidEmail),
                modifier = Modifier.fillMaxSize(),
                onEmailChange = { email = it },
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, bottomInset),
            )
        }

        composeTestRule
            .onNode(hasSetTextAction(), useUnmergedTree = true)
            .performTextInput("person@example.com")
        assertEquals("person@example.com", email)

        val scroll = composeTestRule.onNodeWithTag(AUTH_FORM_SCROLL_TAG, useUnmergedTree = true)
        scroll.performScrollToNode(hasTestTag(AUTH_FORM_ACTION_TAG))
        composeTestRule.onNodeWithTag(AUTH_FORM_ACTION_TAG, useUnmergedTree = true).assertIsDisplayed()

        bottomInset = 120.dp
        composeTestRule.waitForIdle()
        composeTestRule
            .onNode(hasText("person@example.com"), useUnmergedTree = true)
            .assertIsDisplayed()
        scroll.performScrollToNode(hasTestTag(AUTH_FORM_ACTION_TAG))
        composeTestRule.onNodeWithTag(AUTH_FORM_ACTION_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun codeResendAndVerify_remainReachableAfterOverflow() {
        setContainer(height = 280.dp) {
            CodeVerificationContent(
                maskedEmail = "p***@example.com",
                uiState = CodeVerificationUiState(
                    code = "123456",
                    error = AuthFailure.InvalidCode,
                    canResend = true,
                ),
                modifier = Modifier.fillMaxSize(),
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
            )
        }

        composeTestRule
            .onNodeWithTag(AUTH_FORM_SCROLL_TAG, useUnmergedTree = true)
            .performScrollToNode(hasTestTag(AUTH_FORM_ACTION_TAG))
        composeTestRule.onNodeWithTag(AUTH_FORM_ACTION_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun nameValidationGrowth_remainsReachableAfterOverflow() {
        setContainer(height = 280.dp) {
            NameInputContent(
                uiState = NameInputUiState(
                    name = "I",
                    surname = "",
                    nameError = NameFieldError.TooShort,
                    surnameError = NameFieldError.Blank,
                ),
                modifier = Modifier.fillMaxSize(),
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
            )
        }

        composeTestRule
            .onNodeWithTag(AUTH_FORM_SCROLL_TAG, useUnmergedTree = true)
            .performScrollToNode(hasTestTag(AUTH_FORM_ACTION_TAG))
        composeTestRule.onNodeWithTag(AUTH_FORM_ACTION_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    private fun setContainer(
        height: Dp,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        composeTestRule.setContent {
            UIKitTheme {
                Box(
                    modifier = Modifier
                        .size(width = 360.dp, height = height)
                        .testTag(AUTH_FORM_TEST_ROOT_TAG),
                ) {
                    content()
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun rootBounds() =
        composeTestRule
            .onNodeWithTag(AUTH_FORM_TEST_ROOT_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

    private fun actionBounds() =
        composeTestRule
            .onNodeWithTag(AUTH_FORM_ACTION_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
}
