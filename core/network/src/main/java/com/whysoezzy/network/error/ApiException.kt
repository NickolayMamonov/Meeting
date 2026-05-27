package com.whysoezzy.network.error

sealed class ApiException(
    message: String,
) : Exception(message) {
    data class ServerError(
        val errorResponse: ErrorResponse,
    ) : ApiException(errorResponse.message)

    data class NetworkError(
        override val message: String,
    ) : ApiException(message)

    data class UnauthorizedError(
        override val message: String = "Unauthorized",
    ) : ApiException(message)

    data class UnknownError(
        override val message: String,
    ) : ApiException(message)
}
