package com.whysoezzy.domain.models

import java.time.Instant
import java.util.UUID

class PushInstallationFid(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "FID must not be blank" }
    }

    override fun equals(other: Any?): Boolean =
        other is PushInstallationFid && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "PushInstallationFid([redacted])"
}

@JvmInline
value class PushInstallationId(
    val value: String,
) {
    init {
        require(value.isCanonicalLowercaseUuid()) {
            "Installation ID must be a canonical lowercase UUID"
        }
    }
}

data class PushInstallation(
    val installationId: PushInstallationId,
    val status: PushInstallationStatus,
    val lastSeenAt: Instant,
)

enum class PushInstallationStatus {
    ACTIVE,
}

enum class PushInstallationTerminalStatus {
    MALFORMED_SUCCESS,
}

sealed interface PushInstallationUpsertResult {
    data class Acknowledged(
        val installation: PushInstallation,
    ) : PushInstallationUpsertResult

    data class Terminal(
        val status: PushInstallationTerminalStatus,
    ) : PushInstallationUpsertResult
}

sealed interface PushInstallationDeleteResult {
    data object Acknowledged : PushInstallationDeleteResult

    data class Terminal(
        val status: PushInstallationTerminalStatus,
    ) : PushInstallationDeleteResult
}

private fun String.isCanonicalLowercaseUuid(): Boolean =
    length == UUID_STRING_LENGTH &&
        matches(CANONICAL_LOWERCASE_UUID) &&
        runCatching { UUID.fromString(this).toString() == this }.getOrDefault(false)

private const val UUID_STRING_LENGTH = 36
private val CANONICAL_LOWERCASE_UUID =
    Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
