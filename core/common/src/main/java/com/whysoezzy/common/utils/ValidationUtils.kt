package com.whysoezzy.common.utils

object ValidationUtils {
    fun isValidPhoneNumber(phone: String): Boolean {
        val digits = phone.filter { it.isDigit() }
        return digits.length == 11 && digits.startsWith("7")
    }

    fun isValidSmsCode(code: String): Boolean {
        return code.length == 6 && code.all { it.isDigit() }
    }

    fun isValidName(name: String): Boolean {
        return name.isNotBlank() && name.length >= 2
    }
}