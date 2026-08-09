package com.whysoezzy.auth.domain.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.whysoezzy.auth.TokenCrypto
import com.whysoezzy.auth.domain.models.DispatchOutcome
import com.whysoezzy.auth.domain.models.EmailAddress
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

private val Context.pendingEmailOtpDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pending_email_otp_store",
)

/**
 * Stores one generation-checked pending attempt as a single encrypted v1 payload.
 * The email is never written as a plaintext DataStore preference and the OTP is
 * intentionally absent from this schema.
 */
class DataStorePendingEmailOtpStore(
    context: Context,
) : PendingEmailOtpStore {
    private val appContext = context.applicationContext
    private val dataStore get() = appContext.pendingEmailOtpDataStore
    private val cipher = TokenCrypto(appContext)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun replace(attempt: PendingEmailOtpAttempt) {
        dataStore.edit { preferences ->
            preferences[RECORD_KEY] = attempt.toEncryptedRecord()
        }
    }

    override suspend fun get(attemptId: String): PendingEmailOtpAttempt? =
        readRecord()?.takeIf { it.attemptId == attemptId }?.toAttempt()

    override suspend fun getActive(): PendingEmailOtpAttempt? = readRecord()?.toAttempt()

    override suspend fun replaceIfCurrent(
        attemptId: String,
        dispatchGeneration: Long,
        replacement: PendingEmailOtpAttempt,
    ): Boolean {
        var replaced = false
        dataStore.edit { preferences ->
            val current = decodeRecord(preferences)
            if (current?.attemptId == attemptId &&
                current.dispatchGeneration == dispatchGeneration
            ) {
                preferences[RECORD_KEY] = replacement.toEncryptedRecord()
                replaced = true
            }
        }
        return replaced
    }

    override suspend fun clear(attemptId: String, dispatchGeneration: Long?) {
        dataStore.edit { preferences ->
            val current = decodeRecord(preferences)
            if (current?.attemptId == attemptId &&
                (dispatchGeneration == null || current.dispatchGeneration == dispatchGeneration)
            ) {
                preferences.remove(RECORD_KEY)
            }
        }
    }

    override suspend fun clearActive(dispatchGeneration: Long?) {
        dataStore.edit { preferences ->
            val current = decodeRecord(preferences)
            if (dispatchGeneration == null ||
                (current != null && current.dispatchGeneration == dispatchGeneration)
            ) {
                preferences.remove(RECORD_KEY)
            }
        }
    }

    private suspend fun readRecord(): PendingEmailOtpRecord? {
        val encrypted =
            try {
                dataStore.data.first()[RECORD_KEY]
            } catch (_: IOException) {
                return null
            }
                ?: return null
        val record = runCatching {
            json.decodeFromString(PendingEmailOtpRecord.serializer(), cipher.decrypt(encrypted, AAD))
        }.getOrElse {
            clearStored()
            return null
        }
        if (record.version != VERSION) {
            clearStored()
            return null
        }
        return record
    }

    private fun decodeRecord(preferences: Preferences): PendingEmailOtpRecord? {
        val encrypted = preferences[RECORD_KEY] ?: return null
        return runCatching {
            json.decodeFromString(PendingEmailOtpRecord.serializer(), cipher.decrypt(encrypted, AAD))
        }.getOrNull()?.takeIf { it.version == VERSION }
    }

    private fun PendingEmailOtpAttempt.toEncryptedRecord(): String =
        cipher.encrypt(
            json.encodeToString(
                PendingEmailOtpRecord.serializer(),
                PendingEmailOtpRecord(
                    version = VERSION,
                    attemptId = attemptId,
                    email = email.canonical,
                    resendAvailableAtEpochMillis = resendAvailableAtEpochMillis,
                    expiresAtEpochMillis = expiresAtEpochMillis,
                    challengeMayBeActive = challengeMayBeActive,
                    dispatchOutcome = dispatchOutcome,
                    dispatchGeneration = dispatchGeneration,
                ),
            ),
            AAD,
        )

    private fun PendingEmailOtpRecord.toAttempt(): PendingEmailOtpAttempt? =
        runCatching {
            PendingEmailOtpAttempt(
                attemptId = attemptId,
                email = EmailAddress.canonical(email),
                resendAvailableAtEpochMillis = resendAvailableAtEpochMillis,
                expiresAtEpochMillis = expiresAtEpochMillis,
                challengeMayBeActive = challengeMayBeActive,
                dispatchOutcome = dispatchOutcome,
                dispatchGeneration = dispatchGeneration,
            )
        }.getOrNull()

    private suspend fun clearStored() {
        try {
            dataStore.edit { preferences -> preferences.remove(RECORD_KEY) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Cleanup is best effort for corrupt or obsolete records, but cancellation must
            // still abort the caller instead of being reported as a successful clear.
        }
    }

    @Serializable
    private data class PendingEmailOtpRecord(
        val version: Int,
        val attemptId: String,
        val email: String,
        val resendAvailableAtEpochMillis: Long,
        val expiresAtEpochMillis: Long,
        val challengeMayBeActive: Boolean,
        val dispatchOutcome: DispatchOutcome,
        val dispatchGeneration: Long = 0L,
    )

    private companion object {
        const val VERSION = 1
        const val AAD = "pending_email_otp_record_v1"
        val RECORD_KEY = stringPreferencesKey("pending_email_otp_record")
    }
}
