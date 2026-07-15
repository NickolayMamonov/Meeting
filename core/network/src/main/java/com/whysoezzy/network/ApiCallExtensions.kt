package com.whysoezzy.network

import com.whysoezzy.common.error.AppException
import com.whysoezzy.network.error.ErrorResponse
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.json.Json

suspend inline fun <T> safeApiCall(crossinline apiCall: suspend () -> T): Result<T> =
    try {
        Result.success(apiCall())
    } catch (e: ResponseException) {
        val statusCode = e.response.status.value
        when (statusCode) {
            401, 403 -> {
                Result.failure(AppException.UnauthorizedError())
            }

            else -> {
                val errorBody = e.response.bodyAsText()
                val errorResponse =
                    try {
                        Json.decodeFromString<ErrorResponse>(errorBody)
                    } catch (parseException: Exception) {
                        ErrorResponse(
                            status = statusCode,
                            message = e.message ?: "Unknown error",
                            timestamp = "",
                            path = "",
                        )
                    }
                Result.failure(AppException.ServerError(errorResponse.message))
            }
        }
    } catch (e: IOException) {
        Result.failure(AppException.NetworkError(e.message ?: "Network error"))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(AppException.UnknownError(e.message ?: "Unknown error"))
    }
