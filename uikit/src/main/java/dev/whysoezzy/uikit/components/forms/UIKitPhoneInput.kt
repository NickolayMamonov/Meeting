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
    errorMessage: String? = null
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
        maxLines = 1
    )
}

private fun formatPhoneNumber(input: String): String {
    // Remove all non-digit characters
    val digits = input.filter { it.isDigit() }

    return when {
        digits.isEmpty() -> ""
        digits.length <= 1 -> "+7"
        digits.length <= 4 -> "+7 (${digits.substring(1)})"
        digits.length <= 7 -> "+7 (${digits.substring(1, 4)}) ${digits.substring(4)}"
        digits.length <= 9 -> "+7 (${digits.substring(1, 4)}) ${
            digits.substring(
                4,
                7
            )
        }-${digits.substring(7)}"

        else -> "+7 (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${
            digits.substring(
                7,
                9
            )
        }-${digits.substring(9, minOf(11, digits.length))}"
    }
}

@Preview
@Composable
private fun UIKitPhoneInputPreview() {
    UIKitTheme {
        UIKitPhoneInput(
            value = "+7 (999) 123-45-67",
            onValueChange = {},
            modifier = Modifier.fillMaxWidth()
        )
    }
}
