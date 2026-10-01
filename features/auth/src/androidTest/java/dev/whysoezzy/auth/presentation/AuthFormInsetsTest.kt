package dev.whysoezzy.auth.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
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
private val AUTH_FORM_WIDTHS = listOf(320.dp, 360.dp)

@RunWith(AndroidJUnit4::class)
class AuthFormInsetsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun fittingEmail_at320And360_usesMaxOfNavigationAndImeInsets() {
        var width by mutableStateOf(AUTH_FORM_WIDTHS.first())
        val navigationInset = 48.dp
        val imeInset = 180.dp
        setContainer(width = { width }, height = 640.dp) {
            EmailInputContent(
                state = EmailInputUiState(email = "person@example.com"),
                modifier = Modifier.fillMaxSize(),
                windowInsets = unionInsets(navigationInset, imeInset),
            )
        }

        AUTH_FORM_WIDTHS.forEach { testWidth ->
            width = testWidth
            composeTestRule.waitForIdle()
            val root = rootBounds()
            val action = actionBounds()
            val designPadding = with(composeTestRule.density) { SpacingTokens.L.toPx() }
            val navigationPx = with(composeTestRule.density) { navigationInset.toPx() }
            val imePx = with(composeTestRule.density) { imeInset.toPx() }
            val maxObstruction = maxOf(navigationPx, imePx)
            val summedObstruction = navigationPx + imePx

            assertEquals(root.bottom - maxObstruction - designPadding, action.bottom, 1f)
            assertTrue(action.bottom > root.bottom - summedObstruction - designPadding + 1f)
        }
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
    fun overflowingEmail_at320And360_resizesBackToFitAndRetainsSelection() {
        var width by mutableStateOf(AUTH_FORM_WIDTHS.first())
        var email by mutableStateOf("")
        var imeInset by mutableStateOf(360.dp)
        setContainer(width = { width }, height = 640.dp) {
            EmailInputContent(
                state = EmailInputUiState(email = email, error = AuthFailure.InvalidEmail),
                modifier = Modifier.fillMaxSize(),
                onEmailChange = { email = it },
                windowInsets = unionInsets(48.dp, imeInset),
            )
        }

        AUTH_FORM_WIDTHS.forEach { testWidth ->
            width = testWidth
            email = ""
            imeInset = 360.dp
            composeTestRule.waitForIdle()
            val input = composeTestRule.onNode(hasSetTextAction(), useUnmergedTree = true)
            input.performTextInput("person@example.com")
            input.performTextInputSelection(TextRange(7, 7))
            assertEquals("person@example.com", email)

            val scroll = composeTestRule.onNodeWithTag(AUTH_FORM_SCROLL_TAG, useUnmergedTree = true)
            scroll.performScrollToNode(hasTestTag(AUTH_FORM_ACTION_TAG))
            composeTestRule.onNodeWithTag(AUTH_FORM_ACTION_TAG, useUnmergedTree = true).assertIsDisplayed()

            imeInset = 0.dp
            composeTestRule.waitForIdle()
            val designPadding = with(composeTestRule.density) { SpacingTokens.L.toPx() }
            val navigationPx = with(composeTestRule.density) { 48.dp.toPx() }
            assertEquals(rootBounds().bottom - navigationPx - designPadding, actionBounds().bottom, 1f)
            composeTestRule
                .onNode(hasText("person@example.com"), useUnmergedTree = true)
                .assertIsDisplayed()

            input.performTextInput("X")
            assertEquals("person@Xexample.com", email)
        }
    }

    @Test
    fun codeResendAndTimer_at320And360_remainReachableAfterOverflow() {
        var width by mutableStateOf(AUTH_FORM_WIDTHS.first())
        var canResend by mutableStateOf(true)
        var fontScale by mutableStateOf(1f)
        setContainer(width = { width }, height = 300.dp, fontScale = { fontScale }) {
            CodeVerificationContent(
                maskedEmail = "p***@example.com",
                uiState = CodeVerificationUiState(
                    code = "123456",
                    error = AuthFailure.InvalidCode,
                    canResend = canResend,
                    remainingTime = 45,
                ),
                modifier = Modifier.fillMaxSize(),
                windowInsets = unionInsets(48.dp, 180.dp),
            )
        }

        listOf(true, false).forEach { resendState ->
            canResend = resendState
            listOf(1f, 1.5f, 2f).forEach { scale ->
                fontScale = scale
                AUTH_FORM_WIDTHS.forEach { testWidth ->
                    width = testWidth
                    composeTestRule.waitForIdle()
                    val expectedResendText =
                        if (resendState) {
                            "Отправить код повторно"
                        } else {
                            "Отправить повторно через 45 сек"
                        }
                    val scroll = composeTestRule.onNodeWithTag(AUTH_FORM_SCROLL_TAG, useUnmergedTree = true)
                    scroll.performScrollToNode(hasText(expectedResendText))
                    composeTestRule
                        .onNode(hasText(expectedResendText), useUnmergedTree = true)
                        .assertIsDisplayed()
                }
            }
        }
    }

    @Test
    fun nameValidationGrowth_at320And360_remainsReachableAfterOverflow() {
        var width by mutableStateOf(AUTH_FORM_WIDTHS.first())
        setContainer(width = { width }, height = 300.dp) {
            NameInputContent(
                uiState = NameInputUiState(
                    name = "I",
                    surname = "",
                    nameError = NameFieldError.TooShort,
                    surnameError = NameFieldError.Blank,
                ),
                modifier = Modifier.fillMaxSize(),
                windowInsets = unionInsets(48.dp, 180.dp),
            )
        }

        AUTH_FORM_WIDTHS.forEach { testWidth ->
            width = testWidth
            composeTestRule.waitForIdle()
            composeTestRule
                .onNodeWithTag(AUTH_FORM_SCROLL_TAG, useUnmergedTree = true)
                .performScrollToNode(hasTestTag(AUTH_FORM_ACTION_TAG))
            composeTestRule.onNodeWithTag(AUTH_FORM_ACTION_TAG, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    private fun setContainer(
        width: () -> Dp = { 360.dp },
        height: Dp,
        fontScale: () -> Float = { 1f },
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(composeTestRule.density.density, fontScale()),
            ) {
                UIKitTheme {
                    Box(
                        modifier = Modifier
                            .size(width = width(), height = height)
                            .testTag(AUTH_FORM_TEST_ROOT_TAG),
                    ) {
                        content()
                    }
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

    private fun unionInsets(
        navigationBottom: Dp,
        imeBottom: Dp,
    ): WindowInsets =
        WindowInsets(0.dp, 0.dp, 0.dp, navigationBottom)
            .union(WindowInsets(0.dp, 0.dp, 0.dp, imeBottom))

    private fun actionBounds() =
        composeTestRule
            .onNodeWithTag(AUTH_FORM_ACTION_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
}
