package dev.whysoezzy.auth.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import dev.whysoezzy.uikit.tokens.SpacingTokens

internal const val AUTH_FORM_VIEWPORT_TAG = "auth-form-viewport"
internal const val AUTH_FORM_SCROLL_TAG = "auth-form-scroll"
internal const val AUTH_FORM_BODY_TAG = "auth-form-body"
internal const val AUTH_FORM_ACTION_TAG = "auth-form-action"

@Composable
internal fun AuthFormLayout(
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.navigationBars.union(WindowInsets.ime),
    body: @Composable () -> Unit,
    action: @Composable () -> Unit,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .windowInsetsPadding(windowInsets)
            .padding(SpacingTokens.L)
            .testTag(AUTH_FORM_VIEWPORT_TAG),
    ) {
        val viewportHeightPx =
            if (maxHeight.value.isFinite()) {
                with(density) { maxHeight.roundToPx() }
            } else {
                Constraints.Infinity
            }
        val minimumActionGapPx = with(density) { SpacingTokens.L.roundToPx() * 2 }

        Layout(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .testTag(AUTH_FORM_SCROLL_TAG),
            content = {
                Box(
                    modifier = Modifier
                        .testTag(AUTH_FORM_BODY_TAG),
                ) {
                    body()
                }
                Box(
                    modifier = Modifier
                        .testTag(AUTH_FORM_ACTION_TAG),
                ) {
                    action()
                }
            },
        ) { measurables, constraints ->
            val childConstraints = constraints.copy(
                minHeight = 0,
                maxHeight = Constraints.Infinity,
            )
            val bodyPlaceable = measurables[0].measure(childConstraints)
            val actionPlaceable = measurables[1].measure(childConstraints)
            val availableGap = viewportHeightPx - bodyPlaceable.height - actionPlaceable.height
            val actionGap = if (viewportHeightPx == Constraints.Infinity) {
                minimumActionGapPx
            } else {
                maxOf(minimumActionGapPx, availableGap)
            }
            val contentHeight = bodyPlaceable.height + actionGap + actionPlaceable.height
            val contentWidth = if (constraints.maxWidth == Constraints.Infinity) {
                maxOf(
                    constraints.minWidth,
                    maxOf(bodyPlaceable.width, actionPlaceable.width),
                )
            } else {
                constraints.maxWidth
            }

            layout(contentWidth, contentHeight) {
                bodyPlaceable.placeRelative(0, 0)
                actionPlaceable.placeRelative(0, bodyPlaceable.height + actionGap)
            }
        }
    }
}
