package dev.whysoezzy.uikit.components.topbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileTopBar(
    title: String,
    isOwnProfile: Boolean,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        modifier = modifier.fillMaxWidth(),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onBackClick
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        actions = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isOwnProfile) {
                    // Кнопка редактирования для собственного профиля
                    IconButton(
                        onClick = onEditClick
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Редактировать",
                            tint = contentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Кнопка поделиться
                IconButton(
                    onClick = onShareClick
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Поделиться",
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = backgroundColor,
            titleContentColor = contentColor,
            navigationIconContentColor = contentColor,
            actionIconContentColor = contentColor
        )
    )
}

@Preview
@Composable
private fun ProfileTopBarOwnProfilePreview() {
    UIKitTheme {
        ProfileTopBar(
            title = "Иван Петров",
            isOwnProfile = true,
            onBackClick = { },
            onEditClick = { },
            onShareClick = { }
        )
    }
}

@Preview
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

@Preview
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
