package com.whysoezzy.network

import com.whysoezzy.network.error.ApiErrorMetadata
import com.whysoezzy.network.error.ApiException
import com.whysoezzy.network.error.ErrorResponse
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.json.Json

internal const val SERVER_ERROR_MESSAGE = "Server request failed"

internal const val NETWORK_ERROR_MESSAGE = "Network request failed"

internal const val UNKNOWN_ERROR_MESSAGE = "Unexpected request failure"

internal const val REDACTED_METADATA = "[redacted]"

suspend fun <T> safeApiCall(apiCall: suspend () -> T): Result<T> =
    try {
        Result.success(apiCall())
    } catch (e: ResponseException) {
        val statusCode = e.response.status.value
        val responseBody = try {
            e.response.bodyAsText()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ""
        }
        val parsed = try {
            Json { ignoreUnknownKeys = true }.decodeFromString<ErrorResponse>(responseBody)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        when (statusCode) {
            401, 403 -> {
                Result.failure(ApiException.UnauthorizedError())
            }

            else -> {
                val errorResponse = ErrorResponse(
                    status = statusCode,
                    message = SERVER_ERROR_MESSAGE,
                    timestamp = REDACTED_METADATA,
                    path = REDACTED_METADATA,
                    code = parsed?.code,
                )
                Result.failure(
                    ApiException.ServerError(
                        ApiErrorMetadata(status = errorResponse.status, code = errorResponse.code),
                    ),
                )
            }
        }
    } catch (e: IOException) {
        Result.failure(ApiException.NetworkError())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(ApiException.UnknownError())
    }
