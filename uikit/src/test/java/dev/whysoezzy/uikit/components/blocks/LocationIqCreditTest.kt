package dev.whysoezzy.uikit.components.blocks

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.whysoezzy.uikit.theme.DarkColorScheme
import dev.whysoezzy.uikit.theme.LightColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationIqCreditTest {
    @Test
    fun creditColorHasReadableContrastOnLightAndDarkUIKitSurfaces() {
        assertContrastAtLeast(
            foreground = locationIqCreditColor(LightColorScheme.neutralWhite),
            background = LightColorScheme.neutralWhite,
        )
        assertContrastAtLeast(
            foreground = locationIqCreditColor(DarkColorScheme.neutralWhite),
            background = DarkColorScheme.neutralWhite,
        )
    }

    private fun assertContrastAtLeast(
        foreground: Color,
        background: Color,
    ) {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        assertTrue((lighter + 0.05f) / (darker + 0.05f) >= 4.5f)
    }
}
