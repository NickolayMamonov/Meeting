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
        val record = PendingEmailOtpRecord(
            version = VERSION,
            attemptId = attempt.attemptId,
            email = attempt.email.canonical,
            resendAvailableAtEpochMillis = attempt.resendAvailableAtEpochMillis,
            expiresAtEpochMillis = attempt.expiresAtEpochMillis,
            challengeMayBeActive = attempt.challengeMayBeActive,
            dispatchOutcome = attempt.dispatchOutcome,
        )
        val encrypted = cipher.encrypt(
            json.encodeToString(PendingEmailOtpRecord.serializer(), record),
            AAD,
        )
        dataStore.edit { preferences ->
            preferences[RECORD_KEY] = encrypted
        }
    }

    override suspend fun get(attemptId: String): PendingEmailOtpAttempt? {
        val encrypted = dataStore.data.first()[RECORD_KEY] ?: return null
        val record = runCatching {
            json.decodeFromString(PendingEmailOtpRecord.serializer(), cipher.decrypt(encrypted, AAD))
        }.getOrElse {
            clearStored()
            return null
        }
        if (record.version != VERSION || record.attemptId != attemptId) return null
        return runCatching {
            PendingEmailOtpAttempt(
                attemptId = record.attemptId,
                email = EmailAddress.canonical(record.email),
                resendAvailableAtEpochMillis = record.resendAvailableAtEpochMillis,
                expiresAtEpochMillis = record.expiresAtEpochMillis,
                challengeMayBeActive = record.challengeMayBeActive,
                dispatchOutcome = record.dispatchOutcome,
            )
        }.getOrElse {
            clearStored()
            null
        }
    }

    override suspend fun clear(attemptId: String) {
        val current = dataStore.data.first()[RECORD_KEY] ?: return
        val record = runCatching {
            json.decodeFromString(PendingEmailOtpRecord.serializer(), cipher.decrypt(current, AAD))
        }.getOrNull()
        if (record?.attemptId == attemptId) clearStored()
    }

    private suspend fun clearStored() {
        dataStore.edit { preferences -> preferences.remove(RECORD_KEY) }
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
    )

    private companion object {
        const val VERSION = 1
        const val AAD = "pending_email_otp_record_v1"
        val RECORD_KEY = stringPreferencesKey("pending_email_otp_record")
    }
}
