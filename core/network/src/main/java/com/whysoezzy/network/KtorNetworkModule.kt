package com.whysoezzy.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

interface TokenProvider {
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
}

object KtorNetworkModule {

    fun provideHttpClient(
        tokenProvider: TokenProvider? = null,
        onRefreshToken: (suspend () -> Pair<String, String>?)? = null
    ): HttpClient {
        return HttpClient(Android) {
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
                    }
                )
            }

            if (BuildConfig.DEBUG) {
                install(Logging) {
                    level = LogLevel.ALL
                }
            }

            if (tokenProvider != null && onRefreshToken != null) {
                install(Auth) {
                    bearer {
                        loadTokens {
                            val accessToken = tokenProvider.getAccessToken()
                            val refreshToken = tokenProvider.getRefreshToken()

                            if (accessToken != null && refreshToken != null) {
                                BearerTokens(
                                    accessToken = accessToken,
                                    refreshToken = refreshToken
                                )
                            } else {
                                null
                            }
                        }

                        sendWithoutRequest { request ->
                            val baseHost = BuildConfig.BASE_URL
                                .removePrefix("https://")
                                .removePrefix("http://")
                                .substringBefore("/")
                                .substringBefore(":")
                            request.url.host == baseHost
                        }

                        refreshTokens {
                            try {
                                val tokens = onRefreshToken()
                                tokens?.let {
                                    BearerTokens(
                                        accessToken = it.first,
                                        refreshToken = it.second
                                    )
                                }
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                }
            }
        }
    }
}