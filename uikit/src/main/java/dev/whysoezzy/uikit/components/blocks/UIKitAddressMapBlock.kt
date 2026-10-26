package dev.whysoezzy.uikit.components.blocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whysoezzy.uikit.R
import dev.whysoezzy.uikit.components.maps.UIKitMapView
import dev.whysoezzy.uikit.components.maps.launchSafeUrl
import dev.whysoezzy.uikit.components.text.TextHeading2
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.SpacingTokens

private const val LOCATIONIQ_URL = "https://locationiq.com"
internal const val LOCATIONIQ_CREDIT_TEST_TAG = "uikit-locationiq-credit"

internal fun locationIqCreditColor(surfaceColor: Color): Color =
    if (surfaceColor.luminance() > 0.5f) Color(0xFF595959) else Color(0xFFBDBDBD)

/**
 * Блок с адресом и картой
 *
 * @param title Заголовок блока (по умолчанию "Адрес")
 * @param address Адрес места проведения
 * @param latitude Широта для отображения на карте
 * @param longitude Долгота для отображения на карте
 * @param nearestMetro Ближайшая станция метро (опционально)
 * @param onMapClick Колбэк при клике на карту
 * @param modifier Модификатор для кастомизации
 */
@Composable
fun UIKitAddressMapBlock(
    modifier: Modifier = Modifier,
    title: String = "Адрес",
    address: String,
    latitude: Double,
    longitude: Double,
    nearestMetro: String,
    onMapClick: () -> Unit,
    meetingId: Long? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M),
    ) {
        // Адрес
        TextHeading2(text = address)

        // Информация о ближайшем метро
        if (nearestMetro != "Не указано") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.S),
            ) {
                Text(
                    text = "🚇 : $nearestMetro",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        val context = LocalContext.current
        Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.S)) {
            // Карта (без overlay)
            UIKitMapView(
                address = address,
                latitude = latitude,
                longitude = longitude,
                meetingId = meetingId,
                onMapClick = onMapClick,
            )
            Text(
                text = stringResource(R.string.uikit_locationiq_credit),
                color = locationIqCreditColor(MaterialTheme.colorScheme.surface),
                style = MaterialTheme.typography.bodySmall,
                modifier =
                    Modifier
                        .clickable(onClick = { launchSafeUrl(context, LOCATIONIQ_URL) })
                        .padding(vertical = 12.dp)
                        .testTag(LOCATIONIQ_CREDIT_TEST_TAG),
            )
        }
        Text(
            text = "При загрузке карты координаты встречи передаются Mapbox.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Preview
@Composable
private fun UIKitAddressMapBlockPreview() {
    UIKitTheme {
        UIKitAddressMapBlock(
            address = "ул. Тверская, 15, офис 301",
            latitude = 55.7558,
            longitude = 37.6176,
            nearestMetro = "М. Охотный ряд",
            onMapClick = { },
        )
    }
}

@Preview
@Composable
private fun UIKitAddressMapBlockWithoutMetroPreview() {
    UIKitTheme {
        UIKitAddressMapBlock(
            title = "Место проведения",
            address = "ул. Пушкина, д. Колотушкина",
            latitude = 55.7558,
            longitude = 37.6176,
            nearestMetro = "Не указано",
            onMapClick = { },
        )
    }
}
