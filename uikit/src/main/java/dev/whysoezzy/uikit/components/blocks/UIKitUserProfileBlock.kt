package dev.whysoezzy.uikit.components.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.whysoezzy.uikit.components.avatars.UIKitAvatarWithInitials
import dev.whysoezzy.uikit.components.tags.UIKitTagGroup
import dev.whysoezzy.uikit.components.tags.UIKitTagSize
import dev.whysoezzy.uikit.components.text.TextBody1
import dev.whysoezzy.uikit.components.text.TextBody2
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.ColorTokens
import dev.whysoezzy.uikit.tokens.SFProDisplayFontFamily
import dev.whysoezzy.uikit.tokens.SpacingTokens

/**
 * Блок с основной информацией о пользователе.
 * Фото — edge-to-edge (без горизонтальных отступов), контент — с padding.
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
    coverHeight: Int = 375
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Фото — full-width, без горизонтальных отступов
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
            // Заглушка без фото — фон с инициалами по центру
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(coverHeight.dp)
                    .background(ColorTokens.BrandLight),
                contentAlignment = Alignment.Center
            ) {
                UIKitAvatarWithInitials(
                    initials = "${name.firstOrNull() ?: ""}${surname.firstOrNull() ?: ""}",
                    size = 120.dp
                )
            }
        }

        // Контент под фото
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpacingTokens.L)
                .padding(top = SpacingTokens.L),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.S)
        ) {
            // Имя — Inter SemiBold 49sp, letterSpacing -2.45, цвет NeutralActive (#29183B)
            val displayName = buildString {
                append(name)
                if (surname.isNotBlank()) append(" $surname")
            }.trim()

            if (displayName.isNotBlank()) {
                Text(
                    text = displayName,
                    fontFamily = SFProDisplayFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 49.sp,
                    lineHeight = (49 * 0.9).sp,
                    letterSpacing = (-2.45).sp,
                    color = ColorTokens.NeutralActive
                )
            }

            // Город — SemiBold 14sp, чёрный
            if (city.isNotBlank()) {
                TextBody1(text = city)
            }

            // Описание — Medium 14sp, чёрный
            if (description.isNotBlank()) {
                TextBody2(text = description)
            }

            // Интересы — FlowRow теги
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
            description = "Занимаюсь разработкой интерфейсов в eCom. Учу HTML, CSS и JavaScript",
            interests = listOf("Разработка", "Дизайн", "Illustrator", "Backend", "Продакт менеджмент"),
            avatarUrl = "https://picsum.photos/800/400?random=1"
        )
    }
}

@Preview
@Composable
private fun UIKitUserProfileBlockNoAvatarPreview() {
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
