package dev.whysoezzy.uikit.components.maps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whysoezzy.uikit.components.text.TextBody2
import dev.whysoezzy.uikit.components.text.TextMetadata2
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.BorderRadiusTokens
import dev.whysoezzy.uikit.tokens.ColorTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens

@Composable
fun UIKitMapView(
    address: String,
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    onMapClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .height(200.dp)
            .clip(RoundedCornerShape(BorderRadiusTokens.M))
            .background(ColorTokens.NeutralWeak)
            .padding(SpacingTokens.M)
    ) {
        // Placeholder for actual map implementation
        // В реальном проекте здесь будет Google Maps или другая карта
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            TextBody2(
                text = "Map View\n$address\nLat: $latitude, Lng: $longitude",
                color = ColorTokens.NeutralWeak
            )
        }

        // Address overlay
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(
                    Color.White.copy(alpha = 0.9f),
                    RoundedCornerShape(BorderRadiusTokens.S)
                )
                .padding(SpacingTokens.S)
        ) {
            TextMetadata2(
                text = address,
                color = ColorTokens.NeutralWeak
            )
        }
    }
}

@Preview
@Composable
private fun UIKitMapViewPreview() {
    UIKitTheme {
        UIKitMapView(
            address = "ул. Пушкина, д. 10, Москва",
            latitude = 55.7558,
            longitude = 37.6176
        )
    }
}
