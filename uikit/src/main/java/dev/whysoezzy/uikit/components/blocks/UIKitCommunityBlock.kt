package dev.whysoezzy.uikit.components.blocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.whysoezzy.uikit.R
import dev.whysoezzy.uikit.components.text.TextBody2
import dev.whysoezzy.uikit.components.text.TextHeading2
import dev.whysoezzy.uikit.components.text.TextSubheading1
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.ColorTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens

/**
 * Блок с информацией о сообществе/организаторе
 *
 * @param title Заголовок блока (по умолчанию "Организатор")
 * @param communityName Название сообщества
 * @param communityDescription Описание сообщества
 * @param communityImageUrl URL изображения сообщества
 * @param imageSize Размер изображения в dp
 * @param cornerRadius Радиус скругления углов
 * @param backgroundColor Цвет фона блока
 * @param onCommunityClick Колбэк при клике на блок сообщества
 * @param modifier Модификатор для кастомизации
 */
@Composable
fun UIKitCommunityBlock(
    modifier: Modifier = Modifier,
    title: String = "Организатор",
    communityName: String,
    communityDescription: String,
    communityImageUrl: String,
    imageSize: Int = 104,
    cornerRadius: Int = 8,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    onCommunityClick: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M),
    ) {
        // Заголовок
        TextHeading2(text = title)

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onCommunityClick() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M),
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                TextSubheading1(text = communityName, color = Color.Black)
                TextBody2(
                    text = communityDescription,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AsyncImage(
                model = communityImageUrl,
                contentDescription = stringResource(R.string.a11y_community_logo),
                modifier =
                    Modifier
                        .size(imageSize.dp)
                        .clip(RoundedCornerShape(cornerRadius.dp)),
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(ColorTokens.NeutralLine),
                error = ColorPainter(ColorTokens.NeutralLine),
            )
        }
    }
}

@Preview
@Composable
private fun UIKitCommunityBlockPreview() {
    UIKitTheme {
        UIKitCommunityBlock(
            communityName = "Android Developers Moscow",
            communityDescription = "Сообщество разработчиков Android в Москве. " +
                "Мы организуем регулярные встречи, мастер-классы и конференции.",
            communityImageUrl = "https://picsum.photos/300/300?random=community",
            onCommunityClick = { },
        )
    }
}

@Preview
@Composable
private fun UIKitCommunityBlockCustomPreview() {
    UIKitTheme {
        UIKitCommunityBlock(
            title = "Партнер",
            communityName = "JetBrains",
            communityDescription = "Компания-разработчик инструментов разработки ПО",
            communityImageUrl = "https://picsum.photos/300/300?random=jetbrains",
            imageSize = 64,
            cornerRadius = 16,
            backgroundColor = MaterialTheme.colorScheme.primaryContainer,
            onCommunityClick = { },
        )
    }
}

@Preview
@Composable
private fun UIKitCommunityBlockLongDescriptionPreview() {
    UIKitTheme {
        UIKitCommunityBlock(
            communityName = "Flutter Community",
            communityDescription = "Очень длинное описание сообщества, которое должно " +
                "обрезаться после двух строк. Здесь мы рассказываем о том, что " +
                "делаем и какие у нас цели. Еще больше текста для проверки обрезки.",
            communityImageUrl = "https://picsum.photos/300/300?random=flutter",
            onCommunityClick = { },
        )
    }
}
