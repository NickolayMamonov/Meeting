package dev.whysoezzy.uikit.components.maps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.BorderRadiusTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens

@Composable
fun UIKitMapView(
    address: String,
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    onMapClick: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.S),
    ) {
        // Карта
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(BorderRadiusTokens.M))
                    .background(
                        // Имитация цветов карты Google Maps
                        Color(0xFFF0F4F8),
                    ).clickable { onMapClick() },
        ) {
            // Имитация водного объекта (залива)
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            // Цвет воды
                            Color(0xFFAAD3DF),
                        ),
            )

            // Имитация суши
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(start = 120.dp, top = 60.dp, end = 40.dp, bottom = 100.dp)
                        .background(
                            Color(0xFFF0F4F8),
                            RoundedCornerShape(20.dp),
                        ),
            )

            // Имитация дорог (серые линии)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color(0xFFE1E5E9))
                        .align(Alignment.Center),
            )

            Box(
                modifier =
                    Modifier
                        .size(2.dp, 120.dp)
                        .background(Color(0xFFE1E5E9))
                        .align(Alignment.Center),
            )

            // Основной маркер местоположения (красный пин)
            Box(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(40.dp),
            ) {
                // Тень маркера
                Box(
                    modifier =
                        Modifier
                            .size(24.dp)
                            .background(
                                Color.Black.copy(alpha = 0.2f),
                                CircleShape,
                            ).align(Alignment.BottomCenter),
                )

                // Основной маркер
                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .background(
                                Color(0xFFEA4335), // Красный цвет Google
                                CircleShape,
                            ).align(Alignment.TopCenter),
                ) {
                    // Белая точка внутри маркера
                    Box(
                        modifier =
                            Modifier
                                .size(8.dp)
                                .background(Color.White, CircleShape)
                                .align(Alignment.Center),
                    )
                }
            }

            // Дополнительные маркеры (меньшие)
            Box(
                modifier =
                    Modifier
                        .size(20.dp)
                        .background(Color(0xFF4285F4), CircleShape)
                        .align(Alignment.TopEnd)
                        .padding(end = 60.dp, top = 40.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(6.dp)
                            .background(Color.White, CircleShape)
                            .align(Alignment.Center),
                )
            }

            Box(
                modifier =
                    Modifier
                        .size(16.dp)
                        .background(Color(0xFFFBBC04), CircleShape)
                        .align(Alignment.BottomStart)
                        .padding(start = 180.dp, bottom = 80.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(4.dp)
                            .background(Color.White, CircleShape)
                            .align(Alignment.Center),
                )
            }
        }
    }
}

@Preview
@Composable
private fun UIKitMapViewPreview() {
    UIKitTheme {
        UIKitMapView(
            address = "Кожевенная линия, 40",
            latitude = 59.9279,
            longitude = 30.2584,
        )
    }
}

@Preview
@Composable
private fun UIKitMapViewMoscowPreview() {
    UIKitTheme {
        UIKitMapView(
            address = "ул. Тверская, 15, офис 301",
            latitude = 55.7558,
            longitude = 37.6176,
        )
    }
}
