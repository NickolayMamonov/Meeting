package com.whysoezzy.common.utils

object ValidationUtils {
    /**
     * Номер телефона валиден если содержит 11 цифр, начиная с 7 или 8.
     * Бэкенд сам нормализует формат к +7XXXXXXXXXX.
     */
    fun isValidPhoneNumber(phone: String): Boolean {
        val digits = phone.filter { it.isDigit() }
        return (digits.length == 11 && (digits.startsWith("7") || digits.startsWith("8"))) ||
            (digits.length == 10) // без кода страны
    }

    /**
     * OTP-код — 4 цифры (бэкенд генерирует Random.nextInt(1000, 9999))
     */
    fun isValidOtpCode(code: String): Boolean = code.length == 4 && code.all { it.isDigit() }

    // Оставляем для обратной совместимости
    @Deprecated("Use isValidOtpCode instead", ReplaceWith("isValidOtpCode(code)"))
    fun isValidSmsCode(code: String): Boolean = isValidOtpCode(code)

    fun isValidName(name: String): Boolean = name.isNotBlank() && name.length >= 2 && name.all { it.isLetter() || it == '-' || it == ' ' }

    fun isValidSurname(surname: String): Boolean = isValidName(surname)
}
