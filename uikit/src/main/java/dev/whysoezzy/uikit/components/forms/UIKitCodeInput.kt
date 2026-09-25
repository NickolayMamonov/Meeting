package dev.whysoezzy.uikit.components.forms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.autofill.contentType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.whysoezzy.uikit.components.text.TextHeading2
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.BorderRadiusTokens
import dev.whysoezzy.uikit.tokens.ColorTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun UIKitCodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    codeLength: Int = 6,
    isError: Boolean = false,
    contentType: ContentType? = ContentType.SmsOtpCode,
) {
    Box(modifier = modifier.heightIn(min = 56.dp)) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            val layout = codeInputLayout(maxWidth, codeLength, LocalDensity.current)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(CODE_INPUT_ROW_TAG),
                horizontalArrangement = Arrangement.spacedBy(layout.gap, Alignment.CenterHorizontally),
            ) {
                repeat(codeLength) { index ->
                    CodeDigitBox(
                        digit = value.getOrNull(index)?.toString() ?: "",
                        isActive = index == value.length,
                        isError = isError,
                        modifier = Modifier.testTag("$CODE_INPUT_CELL_TAG_PREFIX$index"),
                        size = layout.cellSize,
                    )
                }
            }
        }

        BasicTextField(
            value = value,
            onValueChange = { onValueChange(sanitizeCodeInput(it, codeLength)) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag(CODE_INPUT_INPUT_TAG)
                    .then(
                        if (contentType != null) {
                            Modifier.contentType(contentType)
                        } else {
                            Modifier
                        },
                    ).alpha(0f),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
            cursorBrush = SolidColor(ColorTokens.BrandDark),
        )
    }
}

internal fun sanitizeCodeInput(
    input: String,
    codeLength: Int,
): String = input.filter { it in '0'..'9' }.take(codeLength)

@Composable
private fun CodeDigitBox(
    digit: String,
    isActive: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val borderColor =
        when {
            isError -> ColorTokens.AccentDanger
            isActive -> ColorTokens.BrandDark
            digit.isNotEmpty() -> ColorTokens.NeutralWeak
            else -> ColorTokens.NeutralWeak
        }

    val backgroundColor =
        when {
            isError -> ColorTokens.AccentDanger
            else -> ColorTokens.NeutralWhite
        }

    Box(
        modifier =
            modifier
                .size(size)
                .clip(RoundedCornerShape(BorderRadiusTokens.M))
                .background(backgroundColor)
                .border(
                    width = 2.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(BorderRadiusTokens.M),
                ),
        contentAlignment = Alignment.Center,
    ) {
        TextHeading2(
            text = digit,
            color = ColorTokens.NeutralWeak,
            textAlign = TextAlign.Center,
        )
    }
}

private data class CodeInputLayout(
    val cellSize: Dp,
    val gap: Dp,
)

private fun codeInputLayout(
    maxWidth: Dp,
    codeLength: Int,
    density: Density,
): CodeInputLayout {
    if (codeLength <= 0) {
        return CodeInputLayout(0.dp, 0.dp)
    }

    if (maxWidth == Dp.Infinity) {
        return CodeInputLayout(56.dp, SpacingTokens.S)
    }

    val availableWidthPx = with(density) { maxWidth.toPx().roundToInt() }
    val requestedGapPx = with(density) { floor(SpacingTokens.S.toPx()).toInt() }
    val gapPx = minOf(requestedGapPx, availableWidthPx / (2 * codeLength - 1))
    val maxCellSizePx = with(density) { floor(56.dp.toPx()).toInt() }
    val cellSizePx =
        minOf(
            maxCellSizePx,
            ((availableWidthPx - (codeLength - 1) * gapPx) / codeLength).coerceAtLeast(0),
        )

    return CodeInputLayout(
        cellSize = with(density) { cellSizePx.toDp() },
        gap = with(density) { gapPx.toDp() },
    )
}

private const val CODE_INPUT_ROW_TAG = "UIKitCodeInput.Row"
private const val CODE_INPUT_CELL_TAG_PREFIX = "UIKitCodeInput.Cell."
private const val CODE_INPUT_INPUT_TAG = "UIKitCodeInput.Input"

@Preview
@Composable
private fun UIKitCodeInputPreview() {
    UIKitTheme {
        UIKitCodeInput(
            value = "12",
            onValueChange = {},
        )
    }
}

@Preview
@Composable
private fun UIKitCodeInputErrorPreview() {
    UIKitTheme {
        UIKitCodeInput(
            value = "123456",
            onValueChange = {},
            isError = true,
        )
    }
}
