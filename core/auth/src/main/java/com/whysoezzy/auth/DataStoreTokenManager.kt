package com.whysoezzy.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException

private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "secure_token_store",
)

internal class DataStoreTokenManager(
    context: Context,
) : TokenManager {
    private val appContext = context.applicationContext
    private val dataStore get() = appContext.tokenDataStore
    private val crypto = TokenCrypto(appContext)

    override val isLoggedInFlow: Flow<Boolean> =
        dataStore.data
            .catch { e ->
                if (e is IOException) emit(emptyPreferences()) else throw e
            }.map { prefs -> prefs[KEY_ACCESS_TOKEN] != null }
            .distinctUntilChanged()

    override suspend fun saveTokens(
        accessToken: String,
        refreshToken: String,
        userId: Long?,
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = crypto.encrypt(accessToken, KEY_ACCESS_TOKEN.name)
            prefs[KEY_REFRESH_TOKEN] = crypto.encrypt(refreshToken, KEY_REFRESH_TOKEN.name)
            userId?.let { prefs[KEY_USER_ID] = crypto.encrypt(it.toString(), KEY_USER_ID.name) }
        }
    }

    override suspend fun getAccessToken(): String? = read(KEY_ACCESS_TOKEN)

    override suspend fun getRefreshToken(): String? = read(KEY_REFRESH_TOKEN)

    override suspend fun getUserId(): Long? = read(KEY_USER_ID)?.toLongOrNull()

    override suspend fun clearTokens() {
        dataStore.edit { it.clear() }
    }

    private suspend fun read(key: Preferences.Key<String>): String? {
        val ciphertext =
            dataStore.data
                .catch { e ->
                    if (e is IOException) emit(emptyPreferences()) else throw e
                }.map { prefs -> prefs[key] }
                .first() ?: return null
        return try {
            crypto.decrypt(ciphertext, key.name)
        } catch (e: Exception) {
            // Повреждённый keyset / несовместимый ciphertext — трактуем как «токена нет».
            Timber.w(e, "Token decrypt failed for ${key.name}; treating as absent")
            null
        }
    }

    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
    }
}