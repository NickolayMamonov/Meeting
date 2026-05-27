package dev.whysoezzy.uikit.components.forms

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import dev.whysoezzy.uikit.components.inputs.UIKitInput
import dev.whysoezzy.uikit.components.inputs.UIKitInputState
import dev.whysoezzy.uikit.theme.UIKitTheme

@Composable
fun UIKitPhoneInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Номер телефона",
    state: UIKitInputState = UIKitInputState.EMPTY,
    errorMessage: String? = null,
) {
    UIKitInput(
        value = value,
        onValueChange = { newValue ->
            // Format phone number as user types
            val formatted = formatPhoneNumber(newValue)
            onValueChange(formatted)
        },
        modifier = modifier,
        hint = "",
        errorMessage = errorMessage ?: "",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        maxLines = 1,
    )
}

private fun formatPhoneNumber(input: String): String {
    // Извлекаем только цифры
    val digits = input.filter { it.isDigit() }

    // Нормализуем: убираем ведущую 7/8 если она есть,
    // чтобы работать с 10-значным номером (без кода страны)
    val local =
        when {
            digits.startsWith("7") || digits.startsWith("8") -> digits.drop(1)
            else -> digits
        }.take(10)

    // Строим форматированную строку по мере ввода
    return buildString {
        if (local.isEmpty()) return ""
        append("+7")
        append(" (")
        append(local.take(3))
        if (local.length >= 3) {
            append(") ")
            append(local.substring(3).take(3))
        }
        if (local.length >= 6) {
            append("-")
            append(local.substring(6).take(2))
        }
        if (local.length >= 8) {
            append("-")
            append(local.substring(8).take(2))
        }
    }
}

@Preview
@Composable
private fun UIKitPhoneInputPreview() {
    UIKitTheme {
        UIKitPhoneInput(
            value = "+7 (999) 123-45-67",
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
