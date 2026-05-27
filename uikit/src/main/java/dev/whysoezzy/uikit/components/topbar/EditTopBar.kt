package dev.whysoezzy.uikit.components.topbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whysoezzy.uikit.theme.UIKitTheme

/**
 * TopBar для экранов редактирования с кнопками Отмены и Сохранения
 * ✅ Поддержка прозрачного фона и кастомных цветов
 *
 * @param title Заголовок экрана
 * @param onCancelClick Обработчик кнопки "Отмена" (X)
 * @param onSaveClick Обработчик кнопки "Сохранить" (✓)
 * @param isSaveEnabled Активна ли кнопка сохранения
 * @param containerColor Цвет фона (Color.Transparent для прозрачности)
 * @param contentColor Цвет иконок и текста
 * @param applyStatusBarPadding Применять ли padding для status bar
 * @param modifier Модификатор
 */
@Composable
fun EditTopBar(
    modifier: Modifier = Modifier,
    title: String,
    onCancelClick: () -> Unit,
    onSaveClick: () -> Unit,
    isSaveEnabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    applyStatusBarPadding: Boolean = true,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(containerColor)
                .then(
                    if (applyStatusBarPadding) {
                        Modifier.statusBarsPadding()
                    } else {
                        Modifier
                    },
                ).padding(horizontal = 4.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Кнопка "Отмена" (крестик) слева
        IconButton(
            onClick = onCancelClick,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Отмена",
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
        }

        // Заголовок по центру
        if (title.isNotBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = contentColor,
                modifier = Modifier.weight(1f),
            )
        } else {
            // Пустое пространство если заголовка нет
            androidx.compose.foundation.layout
                .Spacer(modifier = Modifier.weight(1f))
        }

        // Кнопка "Сохранить" (галочка) справа
        IconButton(
            onClick = onSaveClick,
            enabled = isSaveEnabled,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Сохранить",
                tint =
                    if (isSaveEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        contentColor.copy(alpha = 0.38f)
                    },
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Preview(name = "EditTopBar - Default", showBackground = true)
@Composable
private fun EditTopBarDefaultPreview() {
    UIKitTheme {
        EditTopBar(
            title = "Редактирование профиля",
            onCancelClick = { },
            onSaveClick = { },
            isSaveEnabled = true,
        )
    }
}

@Preview(name = "EditTopBar - Transparent", showBackground = false)
@Composable
private fun EditTopBarTransparentPreview() {
    UIKitTheme {
        androidx.compose.foundation.layout.Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color.DarkGray),
        ) {
            EditTopBar(
                title = "",
                onCancelClick = { },
                onSaveClick = { },
                isSaveEnabled = true,
                containerColor = Color.Transparent,
                contentColor = Color.White,
                applyStatusBarPadding = false,
            )
        }
    }
}

@Preview(name = "EditTopBar - Disabled Save", showBackground = true)
@Composable
private fun EditTopBarDisabledPreview() {
    UIKitTheme {
        EditTopBar(
            title = "Редактирование профиля",
            onCancelClick = { },
            onSaveClick = { },
            isSaveEnabled = false,
        )
    }
}
