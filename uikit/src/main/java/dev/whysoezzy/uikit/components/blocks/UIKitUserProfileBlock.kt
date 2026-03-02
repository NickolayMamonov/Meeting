package dev.whysoezzy.uikit.components.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.whysoezzy.uikit.components.avatars.UIKitAvatarWithInitials
import dev.whysoezzy.uikit.components.tags.UIKitTagGroup
import dev.whysoezzy.uikit.components.tags.UIKitTagSize
import dev.whysoezzy.uikit.components.text.TextBody1
import dev.whysoezzy.uikit.components.text.TextBody2
import dev.whysoezzy.uikit.components.text.TextHeading1
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.SpacingTokens


/**
 * Блок с основной информацией о пользователе
 * @param name Имя пользователя
 * @param surname Фамилия пользователя
 * @param city Город пользователя
 * @param description Описание/био пользователя
 * @param avatarUrl URL аватара пользователя (отображается как обложка)
 * @param interests Список интересов (тегов)
 * @param coverHeight Высота обложки в dp (по умолчанию 200dp)
 * @param modifier Модификатор для кастомизации
 */
@Composable
fun UIKitUserProfileBlock(
    modifier: Modifier = Modifier,
    name: String,
    surname: String,
    description: String,
    avatarUrl: String?,
    city: String = "",
    interests: List<String> = emptyList(),
    coverHeight: Int = 280
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Фото профиля",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(coverHeight.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(coverHeight.dp),
                contentAlignment = Alignment.Center
            ) {
                UIKitAvatarWithInitials(
                    initials = "${name.firstOrNull() ?: ""}${surname.firstOrNull() ?: ""}",
                    size = 120.dp
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpacingTokens.L),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            TextHeading1(
                text = "$name $surname",
                textAlign = TextAlign.Center
            )

            if (city.isNotBlank()) {
                TextBody2(
                    text = city,
                    textAlign = TextAlign.Center,
                )
            }

            if (description.isNotBlank()) {
                TextBody1(
                    text = description,
                    textAlign = TextAlign.Center
                )
            }

            if (interests.isNotEmpty()) {
                UIKitTagGroup(
                    tags = interests,
                    size = UIKitTagSize.MEDIUM,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Preview
@Composable
private fun UIKitUserProfileBlockPreview() {
    UIKitTheme {
        UIKitUserProfileBlock(
            name = "Сергей",
            surname = "",
            city = "Москва",
            description = "Занимаюсь разработкой интерфейсов в еСот. Учу HTML, CSS и JavaScript",
            interests = listOf("Разработка", "Дизайн", "Illustrator", "Backend", "Продакт менеджмент"),
            avatarUrl = "https://picsum.photos/800/400?random=1"
        )
    }
}

@Preview
@Composable
private fun UIKitUserProfileBlockWithoutAvatarPreview() {
    UIKitTheme {
        UIKitUserProfileBlock(
            name = "Анна",
            surname = "Иванова",
            city = "Санкт-Петербург",
            description = "UX/UI Designer",
            interests = listOf("Дизайн", "Frontend"),
            avatarUrl = null
        )
    }
}

@Preview
@Composable
private fun UIKitUserProfileBlockMinimalPreview() {
    UIKitTheme {
        UIKitUserProfileBlock(
            name = "Петр",
            surname = "Сидоров",
            description = "",
            avatarUrl = null
        )
    }
}