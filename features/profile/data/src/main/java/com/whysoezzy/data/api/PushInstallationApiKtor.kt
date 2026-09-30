package com.whysoezzy.data.api

import com.whysoezzy.data.dto.PushInstallationRequestDto
import com.whysoezzy.data.dto.PushInstallationResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

internal class PushInstallationApiKtor(
    private val client: HttpClient,
) : PushInstallationApi {
    override suspend fun create(fid: String): PushInstallationUpsertApiResult {
        val response = client.post(PUSH_INSTALLATIONS_PATH) {
            contentType(ContentType.Application.Json)
            setBody(PushInstallationRequestDto(fid))
        }
        return response.validatedUpsert(
            acceptedStatuses = setOf(HttpStatusCode.OK, HttpStatusCode.Created),
        )
    }

    override suspend fun update(
        installationId: String,
        fid: String,
    ): PushInstallationUpsertApiResult {
        val response = client.put("$PUSH_INSTALLATIONS_PATH/$installationId") {
            contentType(ContentType.Application.Json)
            setBody(PushInstallationRequestDto(fid))
        }
        return response.validatedUpsert(
            acceptedStatuses = setOf(HttpStatusCode.OK),
            expectedInstallationId = installationId,
        )
    }

    override suspend fun delete(installationId: String): PushInstallationDeleteApiResult =
        try {
            val response = client.delete("$PUSH_INSTALLATIONS_PATH/$installationId")
            if (response.status == HttpStatusCode.NoContent) {
                PushInstallationDeleteApiResult.Acknowledged
            } else {
                PushInstallationDeleteApiResult.MalformedSuccess
            }
        } catch (error: ClientRequestException) {
            if (error.response.status == HttpStatusCode.NotFound) {
                PushInstallationDeleteApiResult.Acknowledged
            } else {
                throw error
            }
        }

    private suspend fun HttpResponse.validatedUpsert(
        acceptedStatuses: Set<HttpStatusCode>,
        expectedInstallationId: String? = null,
    ): PushInstallationUpsertApiResult {
        if (status !in acceptedStatuses) {
            return PushInstallationUpsertApiResult.MalformedSuccess
        }

        val response = parseStrictResponse(bodyAsText())
            ?: return PushInstallationUpsertApiResult.MalformedSuccess
        if (expectedInstallationId != null && response.installationId != expectedInstallationId) {
            return PushInstallationUpsertApiResult.MalformedSuccess
        }
        return PushInstallationUpsertApiResult.Acknowledged(response)
    }

    private fun parseStrictResponse(body: String): PushInstallationResponseDto? {
        val keys = runCatching { TopLevelJsonKeys.parse(body) }.getOrNull() ?: return null
        if (keys.size != keys.toSet().size ||
            keys.count { it == INSTALLATION_ID } != 1 ||
            keys.count { it == STATUS } != 1 ||
            keys.count { it == LAST_SEEN_AT } != 1 ||
            FID in keys
        ) {
            return null
        }

        val response = runCatching {
            strictJson.decodeFromString<PushInstallationResponseDto>(body)
        }.getOrNull() ?: return null

        if (!response.installationId.isCanonicalLowercaseUuid() ||
            response.status != ACTIVE ||
            !response.lastSeenAt.isStrictUtcInstant()
        ) {
            return null
        }

        return response
    }

    private companion object {
        const val PUSH_INSTALLATIONS_PATH = "profile/push-installations"
        const val INSTALLATION_ID = "installationId"
        const val STATUS = "status"
        const val LAST_SEEN_AT = "lastSeenAt"
        const val FID = "fid"
        const val ACTIVE = "ACTIVE"
        val strictJson = Json {
            isLenient = false
            ignoreUnknownKeys = true
            coerceInputValues = false
        }
    }
}

private fun String.isCanonicalLowercaseUuid(): Boolean =
    length == UUID_STRING_LENGTH &&
        matches(CANONICAL_LOWERCASE_UUID) &&
        runCatching { UUID.fromString(this).toString() == this }.getOrDefault(false)

private fun String.isStrictUtcInstant(): Boolean =
    matches(STRICT_UTC_INSTANT) &&
        runCatching { Instant.parse(this) }.isSuccess

private const val UUID_STRING_LENGTH = 36
private val CANONICAL_LOWERCASE_UUID =
    Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
private val STRICT_UTC_INSTANT =
    Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,3})?Z")

private object TopLevelJsonKeys {
    fun parse(source: String): List<String> = Parser(source).parse()

    private class Parser(
        private val source: String,
    ) {
        private var index = 0

        fun parse(): List<String> {
            skipWhitespace()
            expect('{')
            skipWhitespace()
            if (consume('}')) {
                finish()
                return emptyList()
            }

            val keys = mutableListOf<String>()
            while (true) {
                val keyToken = readStringToken()
                keys += Json.decodeFromString<String>(keyToken)
                skipWhitespace()
                expect(':')
                skipWhitespace()
                skipValue()
                skipWhitespace()
                if (consume('}')) break
                expect(',')
                skipWhitespace()
            }
            finish()
            return keys
        }

        private fun skipValue() {
            when (peek()) {
                '"' -> readStringToken()
                '{' -> skipContainer('{', '}')
                '[' -> skipContainer('[', ']')
                else -> {
                    while (index < source.length && source[index] !in charArrayOf(',', '}', ']')) {
                        index++
                    }
                }
            }
        }

        private fun skipContainer(open: Char, close: Char) {
            expect(open)
            while (true) {
                skipWhitespace()
                if (consume(close)) return
                if (peek() == '"') {
                    readStringToken()
                } else if (peek() == '{') {
                    skipContainer('{', '}')
                } else if (peek() == '[') {
                    skipContainer('[', ']')
                } else {
                    index++
                }
            }
        }

        private fun readStringToken(): String {
            val start = index
            expect('"')
            var escaped = false
            while (index < source.length) {
                val char = source[index++]
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == '"') {
                    return source.substring(start, index)
                }
            }
            error("Unterminated JSON string")
        }

        private fun skipWhitespace() {
            while (index < source.length && source[index].isWhitespace()) index++
        }

        private fun peek(): Char = source.getOrElse(index) { error("Unexpected end of JSON") }

        private fun expect(expected: Char) {
            check(peek() == expected) { "Expected $expected" }
            index++
        }

        private fun consume(expected: Char): Boolean =
            if (index < source.length && source[index] == expected) {
                index++
                true
            } else {
                false
            }

        private fun finish() {
            skipWhitespace()
            check(index == source.length) { "Trailing JSON content" }
        }
    }
}
