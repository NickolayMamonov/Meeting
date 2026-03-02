package com.whysoezzy.auth.domain.repository

import com.whysoezzy.auth.domain.models.AuthResult

interface AuthRepository {
    /** Отправить OTP-код на номер телефона */
    suspend fun sendOtp(phone: String): Result<Unit>

    /** Верифицировать код. Для новых пользователей передаётся name/surname.
     *  Возвращает AuthResult с флагом isNewUser. */
    suspend fun verifyOtp(
        phone: String,
        code: String,
        name: String? = null,
        surname: String? = null
    ): Result<AuthResult>

    /** Обновить access-токен по refresh-токену.
     *  Возвращает новый accessToken (refreshToken остаётся прежним). */
    suspend fun refreshToken(): Result<String>

    /** Выход — инвалидация refresh-токенов на сервере + очистка локального хранилища */
    suspend fun logout()

    fun isLoggedIn(): Boolean
}
