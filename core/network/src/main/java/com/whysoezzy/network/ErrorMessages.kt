package com.whysoezzy.network

import com.whysoezzy.network.error.ApiException


fun Throwable.toUserMessage(): String = when (this) {
    is ApiException.NetworkError ->
        "Нет соединения с сервером. Проверьте подключение к интернету."
    is ApiException.UnauthorizedError ->
        "Сессия истекла. Пожалуйста, войдите снова."
    is ApiException.ServerError ->
        "Ошибка на сервере. Попробуйте позже."
    is ApiException.UnknownError ->
        "Произошла непредвиденная ошибка. Попробуйте позже."
    else ->
        "Произошла ошибка. Попробуйте позже."
}