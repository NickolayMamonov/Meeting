package dev.whysoezzy.uikit.components.topbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whysoezzy.uikit.theme.UIKitTheme

/**
 * Топбар для экрана профиля
 *
 * @param title Заголовок (обычно имя пользователя)
 * @param isOwnProfile Является ли профиль собственным
 * @param onBackClick Колбэк при клике на кнопку назад
 * @param onEditClick Колбэк при клике на кнопку редактирования (только для собственного профиля)
 * @param onShareClick Колбэк при клике на кнопку поделиться
 * @param backgroundColor Цвет фона топбара
 * @param contentColor Цвет контента топбара
 * @param modifier Модификатор для кастомизации
 */
@Composable
fun ProfileTopBar(
    modifier: Modifier = Modifier,
    title: String,
    isOwnProfile: Boolean,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    applyStatusBarPadding: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .then(
                if (applyStatusBarPadding) {
                    Modifier.statusBarsPadding()
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 4.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Левая часть: кнопка назад + заголовок
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.weight(1f)
        ) {
            // Кнопка "Назад"
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Заголовок (если есть)
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
            }
        }

        // Правая часть: кнопки действий
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isOwnProfile) {
                // Кнопка "Редактировать"
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Редактировать",
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Кнопка "Поделиться"
            IconButton(
                onClick = onShareClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Поделиться",
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Preview(name = "Own Profile - Default", showBackground = true)
@Composable
private fun ProfileTopBarOwnProfilePreview() {
    UIKitTheme {
        ProfileTopBar(
            title = "Иван Петров",
            isOwnProfile = true,
            onBackClick = { },
            onEditClick = { },
            onShareClick = { },
        )
    }
}

@Preview(name = "Own Profile - Transparent", showBackground = true)
@Composable
private fun ProfileTopBarTransparentPreview() {
    UIKitTheme {
        ProfileTopBar(
            title = "",
            isOwnProfile = true,
            onBackClick = { },
            onEditClick = { },
            onShareClick = { },
            containerColor = Color.Transparent,
            contentColor = Color.White,
            applyStatusBarPadding = true
        )
    }
}

@Preview(name = "Own Profile - Transparent NO Status Bar Padding", showBackground = true)
@Composable
private fun ProfileTopBarTransparentNoPaddingPreview() {
    UIKitTheme {
        ProfileTopBar(
            title = "",
            isOwnProfile = true,
            onBackClick = { },
            onEditClick = { },
            onShareClick = { },
            containerColor = Color.Transparent,
            contentColor = Color.White,
            applyStatusBarPadding = false
        )
    }
}

@Preview(name = "Other Profile", showBackground = true)
@Composable
private fun ProfileTopBarOtherProfilePreview() {
    UIKitTheme {
        ProfileTopBar(
            title = "Анна Иванова",
            isOwnProfile = false,
            onBackClick = { },
            onShareClick = { }
        )
    }
}

@Preview(name = "Long Name", showBackground = true)
@Composable
private fun ProfileTopBarLongNamePreview() {
    UIKitTheme {
        ProfileTopBar(
            title = "Очень Длинное Имя Пользователя Которое Не Поместится",
            isOwnProfile = true,
            onBackClick = { },
            onEditClick = { },
            onShareClick = { }
        )
    }
}