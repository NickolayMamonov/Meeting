package com.whysoezzy.common.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
* Pure unit-тесты для ValidationUtils — без Android, без coroutines.
* Покрывают публичный контракт: phone / OTP / name / surname.
*/
class ValidationUtilsTest {
    // ==================== isValidPhoneNumber ====================

    @Test
    fun `phone 11 digits starting with 7 is valid`() {
        assertTrue(ValidationUtils.isValidPhoneNumber("79991234567"))
    }

    @Test
    fun `phone 11 digits starting with 8 is valid`() {
        assertTrue(ValidationUtils.isValidPhoneNumber("89991234567"))
    }

    @Test
    fun `phone 10 digits without country code is valid`() {
        assertTrue(ValidationUtils.isValidPhoneNumber("9991234567"))
    }

    @Test
    fun `phone with formatting characters is normalized and valid`() {
        // Внутри функции остаются только цифры — формат от пользователя терпим
        assertTrue(ValidationUtils.isValidPhoneNumber("+7 (999) 123-45-67"))
    }

    @Test
    fun `phone with 11 digits not starting with 7 or 8 is invalid`() {
        assertFalse(ValidationUtils.isValidPhoneNumber("99991234567"))
    }

    @Test
    fun `phone too short is invalid`() {
        assertFalse(ValidationUtils.isValidPhoneNumber("123"))
    }

    @Test
    fun `phone empty is invalid`() {
        assertFalse(ValidationUtils.isValidPhoneNumber(""))
    }

    // ==================== isValidOtpCode ====================

    @Test
    fun `otp six digits is valid`() {
        assertTrue(ValidationUtils.isValidOtpCode("123456"))
    }

    @Test
    fun `otp five digits is invalid`() {
        assertFalse(ValidationUtils.isValidOtpCode("12345"))
    }

    @Test
    fun `otp seven digits is invalid`() {
        assertFalse(ValidationUtils.isValidOtpCode("1234567"))
    }

    @Test
    fun `otp with letters is invalid`() {
        assertFalse(ValidationUtils.isValidOtpCode("123a56"))
    }

    @Test
    fun `otp empty is invalid`() {
        assertFalse(ValidationUtils.isValidOtpCode(""))
    }

    // ==================== isValidName / isValidSurname ====================

    @Test
    fun `name two letters is valid`() {
        assertTrue(ValidationUtils.isValidName("Ян"))
    }

    @Test
    fun `name with hyphen is valid`() {
        assertTrue(ValidationUtils.isValidName("Анна-Мария"))
    }

    @Test
    fun `name with space is valid`() {
        // Текущий контракт: пробелы разрешены (для составных имён).
        assertTrue(ValidationUtils.isValidName("Анна Мария"))
    }

    @Test
    fun `name single letter is invalid`() {
        assertFalse(ValidationUtils.isValidName("А"))
    }

    @Test
    fun `name with digits is invalid`() {
        assertFalse(ValidationUtils.isValidName("Анна1"))
    }

    @Test
    fun `name blank is invalid`() {
        assertFalse(ValidationUtils.isValidName(""))
        assertFalse(ValidationUtils.isValidName("   "))
    }

    @Test
    fun `surname delegates to name validation`() {
        // Sanity: surname использует ту же логику
        assertTrue(ValidationUtils.isValidSurname("Иванов"))
        assertFalse(ValidationUtils.isValidSurname("И"))
    }
}
