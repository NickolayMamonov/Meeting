package com.whysoezzy.common.error

sealed class ErrorType {
    data object NoConnection : ErrorType()
    data object Unauthorized : ErrorType()
    data object Server : ErrorType()
    data object Unknown : ErrorType()
}