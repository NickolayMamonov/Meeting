package com.whysoezzy.common.error

fun Throwable.toErrorType(): ErrorType =
    when (this) {
        is AppException.NetworkError -> ErrorType.NoConnection
        is AppException.UnauthorizedError -> ErrorType.Unauthorized
        is AppException.ServerError -> ErrorType.Server
        is AppException.UnknownError -> ErrorType.Unknown
        else -> ErrorType.Unknown
    }
