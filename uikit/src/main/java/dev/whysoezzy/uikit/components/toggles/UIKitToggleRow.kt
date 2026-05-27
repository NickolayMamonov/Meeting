package dev.whysoezzy.uikit.components.toggles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.SpacingTokens

/**
 * Toggle с текстовой меткой слева
 * Используется для настроек и опций в профиле
 *
 * @param label Текст метки слева
 * @param checked Состояние переключателя
 * @param onCheckedChange Callback при изменении состояния
 * @param modifier Модификатор
 * @param enabled Активен ли переключатель
 */
@Composable
fun UIKitToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    onValueChange = onCheckedChange,
                    enabled = enabled,
                    role = Role.Switch,
                ).padding(vertical = SpacingTokens.S),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(end = SpacingTokens.M),
        )

        UIKitToggle(
            checked = checked,
            onCheckedChange = {},
            enabled = enabled,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

@Preview(name = "UIKitToggleRow - Unchecked", showBackground = true)
@Composable
private fun UIKitToggleRowUncheckedPreview() {
    UIKitTheme {
        UIKitToggleRow(
            label = "Показывать мои сообщества",
            checked = false,
            onCheckedChange = { },
        )
    }
}

@Preview(name = "UIKitToggleRow - Checked", showBackground = true)
@Composable
private fun UIKitToggleRowCheckedPreview() {
    UIKitTheme {
        UIKitToggleRow(
            label = "Включить уведомления",
            checked = true,
            onCheckedChange = { },
        )
    }
}

@Preview(name = "UIKitToggleRow - Interactive", showBackground = true)
@Composable
private fun UIKitToggleRowInteractivePreview() {
    UIKitTheme {
        var checked by remember { mutableStateOf(false) }
        UIKitToggleRow(
            label = "Показывать мои встречи",
            checked = checked,
            onCheckedChange = { checked = it },
        )
    }
}

@Preview(name = "UIKitToggleRow - Disabled", showBackground = true)
@Composable
private fun UIKitToggleRowDisabledPreview() {
    UIKitTheme {
        UIKitToggleRow(
            label = "Отключенная опция",
            checked = true,
            onCheckedChange = { },
            enabled = false,
        )
    }
}

@Preview(name = "UIKitToggleRow - Long Label", showBackground = true)
@Composable
private fun UIKitToggleRowLongLabelPreview() {
    UIKitTheme {
        UIKitToggleRow(
            label = "Очень длинная метка которая может не поместиться в одну строку и перейти на вторую",
            checked = true,
            onCheckedChange = { },
        )
    }
}
