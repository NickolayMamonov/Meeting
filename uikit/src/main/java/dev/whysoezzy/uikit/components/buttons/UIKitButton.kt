package dev.whysoezzy.uikit.components.buttons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.whysoezzy.uikit.theme.PrimaryGradient
import dev.whysoezzy.uikit.theme.SecondaryGradient
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.BorderRadiusTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens

enum class UIKitButtonState {
    PRIMARY,
    SECONDARY,
    LOADING,
    DISABLED
}

@Composable
fun UIKitButton(
    text: String,
    modifier: Modifier = Modifier,
    state: UIKitButtonState = UIKitButtonState.PRIMARY,
    onClick: () -> Unit = {}
) {
    val colorScheme = UIKitTheme.colors

    Box(
        modifier = modifier
            .width(343.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(BorderRadiusTokens.L))
            .then(
                when (state) {
                    UIKitButtonState.PRIMARY, UIKitButtonState.LOADING -> Modifier.background(
                        brush = PrimaryGradient,
                    )

                    UIKitButtonState.SECONDARY -> Modifier.background(
                        brush = SecondaryGradient,
                    )

                    UIKitButtonState.DISABLED -> Modifier.background(
                        color = colorScheme.neutralLine,
                    )
                }
            )
            .clickable(
                enabled = state != UIKitButtonState.DISABLED && state != UIKitButtonState.LOADING,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .padding(
                    start = SpacingTokens.XL,
                    end = SpacingTokens.XL,
                    top = SpacingTokens.M,
                    bottom = 18.dp
                ),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (state) {
                UIKitButtonState.LOADING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = colorScheme.neutralWhite,
                        strokeWidth = 2.dp
                    )
                }

                else -> {
                    Text(
                        text = text,
                        color = when (state) {
                            UIKitButtonState.SECONDARY -> colorScheme.brandDark
                            UIKitButtonState.DISABLED -> colorScheme.neutralDisabled
                            else -> colorScheme.neutralWhite
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UIKitButtonPreview() {
    UIKitTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingTokens.M),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            UIKitButton(
                text = "Primary Button",
                state = UIKitButtonState.PRIMARY
            )

            UIKitButton(
                text = "Secondary Button",
                state = UIKitButtonState.SECONDARY
            )

            UIKitButton(
                text = "Loading",
                state = UIKitButtonState.LOADING
            )

            UIKitButton(
                text = "Disabled Button",
                state = UIKitButtonState.DISABLED
            )
        }
    }
}