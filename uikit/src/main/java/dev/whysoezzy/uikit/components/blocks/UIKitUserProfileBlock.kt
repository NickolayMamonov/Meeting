package dev.whysoezzy.uikit.components.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.whysoezzy.uikit.components.avatars.UIKitAvatarWithInitials
import dev.whysoezzy.uikit.components.text.TextBody1
import dev.whysoezzy.uikit.components.text.TextHeading1
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.SpacingTokens

/**
 * Блок с основной информацией о пользователе
 *
 * @param name Имя пользователя
 * @param surname Фамилия пользователя
 * @param description Описание/био пользователя
 * @param avatarUrl URL аватара пользователя
 * @param avatarSize Размер аватара в dp
 * @param modifier Модификатор для кастомизации
 */
@Composable
fun UIKitUserProfileBlock(
    name: String,
    surname: String,
    description: String,
    avatarUrl: String?,
    avatarSize: Int = 120,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
    ) {
        // Аватар пользователя
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Аватар пользователя",
                modifier = Modifier
                    .size(avatarSize.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            UIKitAvatarWithInitials(
                initials = "${name.firstOrNull() ?: ""}${surname.firstOrNull() ?: ""}",
                size = avatarSize.dp
            )
        }

        // Имя и фамилия
        TextHeading1(
            text = "$name $surname",
            textAlign = TextAlign.Center
        )

        // Описание
        if (description.isNotBlank()) {
            TextBody1(
                text = description,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = SpacingTokens.L)
            )
        }
    }
}

@Preview
@Composable
private fun UIKitUserProfileBlockPreview() {
    UIKitTheme {
        UIKitUserProfileBlock(
            name = "Иван",
            surname = "Петров",
            description = "Senior Android Developer в крутой компании. Люблю изучать новые технологии и делиться знаниями с сообществом.",
            avatarUrl = "https://picsum.photos/200/200?random=1"
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
            description = "UX/UI Designer",
            avatarUrl = null,
            avatarSize = 100
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
