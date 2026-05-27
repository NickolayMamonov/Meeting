package com.whysoezzy.network

import com.whysoezzy.common.error.ErrorType
import com.whysoezzy.network.error.ApiException

fun Throwable.toErrorType(): ErrorType =
    when (this) {
        is ApiException.NetworkError -> ErrorType.NoConnection
        is ApiException.UnauthorizedError -> ErrorType.Unauthorized
        is ApiException.ServerError -> ErrorType.Server
        is ApiException.UnknownError -> ErrorType.Unknown
        else -> ErrorType.Unknown
    }

/**
 * Локализованное сообщение по умолчанию. Использовать в ViewModel,
 * которые ещё не переведены на ErrorType + Composable stringResource.
 * Полностью убрать после завершения R-020.
 */
fun Throwable.toUserMessage(): String =
    when (toErrorType()) {
        ErrorType.NoConnection -> "Нет соединения с сервером. Проверьте подключение к интернету."
        ErrorType.Unauthorized -> "Сессия истекла. Пожалуйста, войдите снова."
        ErrorType.Server -> "Ошибка на сервере. Попробуйте позже."
        ErrorType.Unknown -> "Произошла непредвиденная ошибка. Попробуйте позже."
    }
