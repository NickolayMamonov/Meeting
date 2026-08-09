package dev.whysoezzy.uikit.components.forms

import org.junit.Assert.assertEquals
import org.junit.Test

class UIKitCodeInputTest {
    @Test
    fun `keeps up to six ASCII digits`() {
        assertEquals("123456", sanitizeCodeInput("1234567", codeLength = 6))
    }

    @Test
    fun `extracts a whole code from pasted or autofilled text`() {
        assertEquals("123456", sanitizeCodeInput("Your code is 123-456", codeLength = 6))
    }

    @Test
    fun `rejects non ASCII numerals`() {
        assertEquals("123", sanitizeCodeInput("1٢2３3", codeLength = 6))
    }

    @Test
    fun `supports deletion to an empty value`() {
        assertEquals("", sanitizeCodeInput("", codeLength = 6))
    }
}
