package dev.whysoezzy.uikit.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun UIKitTheme(
    colorScheme: UIKitColorScheme = LightColorScheme,
    typography: UIKitTypography = DefaultTypography,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalUIKitColorScheme provides colorScheme,
        LocalUIKitTypography provides typography
    ) {
        MaterialTheme(
            content = content
        )
    }
}

object UIKitTheme {
    val colors: UIKitColorScheme
        @Composable get() = LocalUIKitColorScheme.current
    
    val typography: UIKitTypography
        @Composable get() = LocalUIKitTypography.current
}