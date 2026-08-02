package com.whysoezzy.auth.domain.models

import java.net.IDN
import java.text.Normalizer
import java.util.Locale

class EmailAddress private constructor(
    internal val canonical: String,
) {
    val masked: String = mask(canonical)

    override fun toString(): String = masked

    override fun equals(other: Any?): Boolean = other is EmailAddress && canonical == other.canonical

    override fun hashCode(): Int = canonical.hashCode()

    companion object {
        internal fun canonical(value: String): EmailAddress = EmailAddress(value)

        private fun mask(email: String): String {
            val local = email.substringBefore('@')
            val domain = email.substringAfter('@')
            return "${local.first()}***@$domain"
        }
    }
}

class EmailAddressParser {
    fun parse(raw: String): AuthOutcome<EmailAddress> {
        val normalized = Normalizer.normalize(raw.trim { Character.isWhitespace(it) }, Normalizer.Form.NFC)
        if (normalized.count { it == '@' } != 1) return invalid()

        val local = normalized.substringBefore('@')
        val rawDomain = normalized.substringAfter('@')
        if (!isValidLocal(local)) return invalid()

        val domain =
            try {
                IDN.toASCII(rawDomain, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
            } catch (_: IllegalArgumentException) {
                return invalid()
            }
        if (!isValidDomain(domain)) return invalid()

        val canonical = "${local.lowercase(Locale.ROOT)}@$domain"
        return if (canonical.length <= MAX_ADDRESS_LENGTH) {
            AuthOutcome.Success(EmailAddress.canonical(canonical))
        } else {
            invalid()
        }
    }

    private fun isValidLocal(local: String): Boolean =
        local.length in 1..MAX_LOCAL_LENGTH &&
            local.split('.').all { atom ->
                atom.isNotEmpty() && atom.all { character -> character.code < 128 && character in ATEXT }
            }

    private fun isValidDomain(domain: String): Boolean {
        if (domain.length !in 1..MAX_DOMAIN_LENGTH || domain.startsWith('.') || domain.endsWith('.')) return false
        val labels = domain.split('.')
        return labels.size >= 2 &&
            labels.all { label ->
                label.length in 1..MAX_LABEL_LENGTH &&
                    label.first().isAsciiLetterOrDigit() &&
                    label.last().isAsciiLetterOrDigit() &&
                    label.all { it.isAsciiLetterOrDigit() || it == '-' }
            }
    }

    private fun Char.isAsciiLetterOrDigit(): Boolean = this in 'a'..'z' || this in '0'..'9'

    private fun invalid(): AuthOutcome.Failure = AuthOutcome.Failure(AuthFailure.InvalidEmail)

    private companion object {
        const val MAX_LOCAL_LENGTH = 64
        const val MAX_LABEL_LENGTH = 63
        const val MAX_DOMAIN_LENGTH = 253
        const val MAX_ADDRESS_LENGTH = 254
        const val ATEXT = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!#$%&'*+-/=?^_`{|}~"
    }
}
