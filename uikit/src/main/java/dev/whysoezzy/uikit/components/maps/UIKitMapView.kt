package dev.whysoezzy.uikit.components.maps

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.BorderRadiusTokens
import dev.whysoezzy.uikit.tokens.ColorTokens
import java.util.Locale

private val MapLand = Color(0xFFF4F1EB)
private val MapBuilding = Color(0xFFE4DFD5)
private val MapBuildingOutline = Color(0xFFD5CFC3)
private val MapPark = Color(0xFFD5E6CA)
private val MapWater = Color(0xFFB9DDE6)
private val MapInk = Color(0xFF56605C)

/**
 * Deterministic illustrative map, not a geographic tile or a navigation service.
 * The supplied coordinates describe the meeting, not the fictional street layout.
 */
@Composable
fun UIKitMapView(
    address: String,
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    onMapClick: () -> Unit = {},
) {
    val coordinates = String.format(Locale.ROOT, "%.4f, %.4f", latitude, longitude)
    val labelPaint =
        remember {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(86, 96, 92)
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(BorderRadiusTokens.M))
                .background(MapLand)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Демонстрационная схема. $address. Координаты: $coordinates"
                }.clickable(onClickLabel = "Открыть место встречи", onClick = onMapClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // A fixed artboard makes every device and recording show the same district.
            withTransform({
                scale(size.width / 400f, size.height / 240f, pivot = Offset.Zero)
            }) {
                drawRect(MapLand, size = Size(400f, 240f))

                val park =
                    Path().apply {
                        moveTo(270f, 0f)
                        lineTo(400f, 0f)
                        lineTo(400f, 132f)
                        lineTo(326f, 110f)
                        close()
                    }
                drawPath(park, MapPark)
                val river =
                    Path().apply {
                        moveTo(285f, 240f)
                        cubicTo(290f, 197f, 343f, 210f, 357f, 168f)
                        cubicTo(371f, 133f, 394f, 146f, 414f, 119f)
                    }
                drawPath(river, MapWater, style = Stroke(width = 34f))

                val blocks =
                    listOf(
                        Offset(17f, 42f) to Size(44f, 27f),
                        Offset(76f, 40f) to Size(50f, 30f),
                        Offset(18f, 88f) to Size(42f, 42f),
                        Offset(77f, 87f) to Size(48f, 23f),
                        Offset(78f, 120f) to Size(45f, 32f),
                        Offset(165f, 24f) to Size(39f, 40f),
                        Offset(218f, 25f) to Size(38f, 22f),
                        Offset(166f, 85f) to Size(29f, 20f),
                        Offset(220f, 75f) to Size(41f, 36f),
                        Offset(169f, 149f) to Size(33f, 33f),
                        Offset(219f, 146f) to Size(39f, 22f),
                        Offset(274f, 127f) to Size(36f, 31f),
                        Offset(19f, 182f) to Size(42f, 24f),
                        Offset(78f, 180f) to Size(48f, 27f),
                        Offset(218f, 186f) to Size(39f, 33f),
                    )
                blocks.forEach { (position, dimensions) ->
                    drawRoundRect(
                        MapBuilding,
                        topLeft = position,
                        size = dimensions,
                        cornerRadius = CornerRadius(3f),
                    )
                    drawRoundRect(
                        MapBuildingOutline,
                        topLeft = position,
                        size = dimensions,
                        cornerRadius = CornerRadius(3f),
                        style = Stroke(0.8f),
                    )
                }

                val boulevard =
                    Path().apply {
                        moveTo(143f, -10f)
                        cubicTo(153f, 76f, 139f, 160f, 163f, 250f)
                    }
                val crossStreet =
                    Path().apply {
                        moveTo(-10f, 163f)
                        cubicTo(122f, 165f, 242f, 111f, 410f, 122f)
                    }
                listOf(boulevard, crossStreet).forEach { road ->
                    drawPath(road, Color(0xFFDDD6C7), style = Stroke(19f))
                    drawPath(road, Color.White, style = Stroke(16f))
                }
                drawLine(Color.White, Offset(0f, 78f), Offset(129f, 78f), 9f)
                drawLine(Color.White, Offset(68f, 25f), Offset(68f, 230f), 8f)
                drawLine(Color.White, Offset(210f, 0f), Offset(210f, 240f), 9f)
                drawLine(Color.White, Offset(158f, 70f), Offset(275f, 60f), 9f)
                drawLine(Color(0xFFF9F8EF), Offset(284f, 18f), Offset(369f, 111f), 5f)
                drawLine(Color(0xFFF9F8EF), Offset(328f, 13f), Offset(302f, 89f), 4f)

                listOf(
                    Offset(289f, 30f),
                    Offset(341f, 28f),
                    Offset(371f, 39f),
                    Offset(310f, 59f),
                    Offset(358f, 75f),
                    Offset(384f, 92f),
                    Offset(282f, 90f),
                ).forEach { tree ->
                    drawCircle(Color(0xFFB8D3AA), 8f, tree + Offset(1f, 2f))
                    drawCircle(Color(0xFFC1DDB2), 7f, tree)
                }
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText("СКВЕР", 327f, 59f, labelPaint)
                    canvas.nativeCanvas.drawText("Бульвар", 158f, 213f, labelPaint)
                    canvas.nativeCanvas.drawText("Пешеходная улица", 22f, 151f, labelPaint)
                    canvas.nativeCanvas.drawText("Кофейня", 81f, 137f, labelPaint)
                }

                // The pin tip marks the meeting; the surrounding halo stays unobtrusive.
                val destination = Offset(201f, 130f)
                drawCircle(ColorTokens.BrandDefault.copy(alpha = 0.12f), 27f, destination)
                drawCircle(ColorTokens.BrandDefault.copy(alpha = 0.16f), 17f, destination)
                drawOval(
                    MapInk.copy(alpha = 0.16f),
                    topLeft = destination + Offset(-11f, -3f),
                    size = Size(22f, 7f),
                )
                val pin =
                    Path().apply {
                        moveTo(201f, 131f)
                        cubicTo(196f, 123f, 183f, 111f, 183f, 100f)
                        cubicTo(183f, 76f, 219f, 76f, 219f, 100f)
                        cubicTo(219f, 111f, 206f, 123f, 201f, 131f)
                        close()
                    }
                drawPath(pin, Color.White, style = Stroke(4f))
                drawPath(pin, ColorTokens.BrandDark)
                drawCircle(Color.White, 6f, Offset(201f, 99f))
            }
        }
        Text(
            text = "Схема района · демо",
            color = MapInk,
            fontSize = 11.sp,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(Color.White.copy(alpha = 0.94f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Text(
            text = coordinates,
            color = MapInk,
            fontSize = 10.sp,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .background(Color.White.copy(alpha = 0.94f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Preview
@Composable
private fun UIKitMapViewPreview() {
    UIKitTheme {
        UIKitMapView(
            address = "Москва, ул. Покровка, 27",
            latitude = 55.7617,
            longitude = 37.6475,
        )
    }
}
