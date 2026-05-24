package com.whysoezzy.network

import com.whysoezzy.common.error.ErrorType
import com.whysoezzy.network.error.ApiException

fun Throwable.toErrorType(): ErrorType = when (this) {
    is ApiException.NetworkError -> ErrorType.NoConnection
    is ApiException.UnauthorizedError -> ErrorType.Unauthorized
    is ApiException.ServerError -> ErrorType.Server
    is ApiException.UnknownError -> ErrorType.Unknown
    else -> ErrorType.Unknown
}