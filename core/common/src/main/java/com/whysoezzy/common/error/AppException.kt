package com.whysoezzy.common.error

sealed class AppException(
    message: String,
) : Exception(message) {
    data class ServerError(
        override val message: String,
    ) : AppException(message)

    data class NetworkError(
        override val message: String,
    ) : AppException(message)

    data class UnauthorizedError(
        override val message: String = "Unauthorized",
    ) : AppException(message)

    data class UnknownError(
        override val message: String,
    ) : AppException(message)
}
