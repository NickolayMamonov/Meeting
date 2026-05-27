package dev.whysoezzy.uikit.components.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.whysoezzy.uikit.components.maps.UIKitMapView
import dev.whysoezzy.uikit.components.text.TextHeading2
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.SpacingTokens

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

        // Карта (без overlay)
        UIKitMapView(
            address = address,
            latitude = latitude,
            longitude = longitude,
            onMapClick = onMapClick,
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
