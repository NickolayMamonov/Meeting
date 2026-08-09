package com.whysoezzy.network.error

import com.whysoezzy.common.error.ErrorType
import com.whysoezzy.common.error.ErrorTypeCarrier

sealed class ApiException private constructor(
    message: String,
) : Exception(message, null, false, false),
    ErrorTypeCarrier {
    class ServerError(
        val metadata: ApiErrorMetadata,
    ) : ApiException("Server request failed") {
        override val errorType: ErrorType = ErrorType.Server

        override fun toString(): String = "ServerError(status=${metadata.status}, code=${metadata.code})"
    }

    class NetworkError : ApiException("Network request failed") {
        override val errorType: ErrorType = ErrorType.NoConnection

        override fun toString(): String = "NetworkError"
    }

    class UnauthorizedError(
        val metadata: ApiErrorMetadata? = null,
    ) : ApiException("Unauthorized") {
        override val errorType: ErrorType = ErrorType.Unauthorized

        override fun toString(): String = "UnauthorizedError"
    }

    class UnknownError : ApiException("Unexpected request failure") {
        override val errorType: ErrorType = ErrorType.Unknown

        override fun toString(): String = "UnknownError"
    }
}
