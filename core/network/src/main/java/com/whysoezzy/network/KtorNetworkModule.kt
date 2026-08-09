package com.whysoezzy.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpMethod
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

interface TokenProvider {
    suspend fun getAccessToken(): String?

    suspend fun getRefreshToken(): String?

    suspend fun loadTokens(): TokenSnapshot?
}

class TokenSnapshot(
    val accessToken: String,
    val refreshToken: String,
) {
    override fun toString(): String = "TokenSnapshot(redacted)"
}

object KtorNetworkModule {
    fun provideHttpClient(
        tokenProvider: TokenProvider? = null,
        onRefreshToken: (suspend () -> Pair<String, String>?)? = null,
    ): HttpClient =
        HttpClient(Android) {
            configure(tokenProvider, onRefreshToken)
        }

    fun provideHttpClient(
        engine: HttpClientEngine,
        tokenProvider: TokenProvider? = null,
        onRefreshToken: (suspend () -> Pair<String, String>?)? = null,
    ): HttpClient =
        HttpClient(engine) {
            configure(tokenProvider, onRefreshToken)
        }

    private fun HttpClientConfig<*>.configure(
        tokenProvider: TokenProvider?,
        onRefreshToken: (suspend () -> Pair<String, String>?)?,
    ) {
        expectSuccess = true

        defaultRequest {
            url(BuildConfig.BASE_URL)
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }

        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = BuildConfig.DEBUG
                    isLenient = true
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                    encodeDefaults = false
                },
            )
        }
        install(HttpRequestRetry) {
            retryIf(maxRetries = MAX_RETRIES) { request, response ->
                request.method.isRetryable() &&
                    !request.url.encodedPath.isAuthPath() &&
                    response.status.value in SERVER_ERROR_STATUS_RANGE
            }
            retryOnExceptionIf(maxRetries = MAX_RETRIES) { request, cause ->
                request.method.isRetryable() &&
                    !request.url.encodedPath.isAuthPath() &&
                    cause !is CancellationException
            }
            exponentialDelay()
        }

        if (BuildConfig.DEBUG) {
            install(Logging) {
                level = LogLevel.INFO
                filter { request -> !request.url.encodedPath.isAuthPath() }
            }
        }

        if (tokenProvider != null && onRefreshToken != null) {
            install(Auth) {
                bearer {
                    loadTokens {
                        tokenProvider.loadTokens()?.let {
                            BearerTokens(
                                accessToken = it.accessToken,
                                refreshToken = it.refreshToken,
                            )
                        }
                    }

                    sendWithoutRequest { request ->
                        request.url.host == baseHost() &&
                            !request.url.encodedPath.isAuthPath()
                    }

                    refreshTokens {
                        try {
                            val tokens = onRefreshToken()
                            tokens?.let {
                                BearerTokens(
                                    accessToken = it.first,
                                    refreshToken = it.second,
                                )
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.e("Bearer refresh callback failed")
                            null
                        }
                    }
                }
            }
        }
    }

    private fun HttpMethod.isRetryable(): Boolean =
        this == HttpMethod.Get ||
            this == HttpMethod.Head ||
            this == HttpMethod.Options ||
            this == HttpMethod.Put ||
            this == HttpMethod.Delete

    private fun String.isAuthPath(): Boolean =
        split('/')
            .any { segment -> segment.equals(AUTH_PATH_SEGMENT, ignoreCase = true) }

    private fun baseHost(): String =
        BuildConfig.BASE_URL
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .substringBefore(":")

    private const val AUTH_PATH_SEGMENT = "auth"
    private const val MAX_RETRIES = 3
    private val SERVER_ERROR_STATUS_RANGE = 500..599
}
