package com.whysoezzy.common.error

sealed interface ErrorType {
    data object NoConnection : ErrorType

    data object Unauthorized : ErrorType

    data object Server : ErrorType

    data object Unknown : ErrorType
}

interface ErrorTypeCarrier {
    val errorType: ErrorType
}

fun Throwable.toErrorType(): ErrorType =
    (this as? ErrorTypeCarrier)?.errorType ?: ErrorType.Unknown
