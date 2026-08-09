package com.whysoezzy.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.whysoezzy.auth.domain.models.AuthSession
import com.whysoezzy.network.TokenSnapshot
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
            }.map(::hasValidTokenPair)
            .distinctUntilChanged()

    override val session: Flow<AuthSession> =
        dataStore.data
            .catch { e ->
                if (e is IOException) emit(emptyPreferences()) else throw e
            }.map(::decodeSession)
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

    override suspend fun saveAuthenticated(
        accessToken: String,
        refreshToken: String,
        userId: Long,
        stage: AuthSession.Stage,
    ) {
        require(stage != AuthSession.Stage.LoggedOut)
        dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = crypto.encrypt(accessToken, KEY_ACCESS_TOKEN.name)
            prefs[KEY_REFRESH_TOKEN] = crypto.encrypt(refreshToken, KEY_REFRESH_TOKEN.name)
            prefs[KEY_USER_ID] = crypto.encrypt(userId.toString(), KEY_USER_ID.name)
            prefs[KEY_STAGE] = crypto.encrypt(stage.name, KEY_STAGE.name)
        }
    }

    override suspend fun readSession(): AuthSession {
        val preferences = dataStore.data
            .catch { e ->
                if (e is IOException) emit(emptyPreferences()) else throw e
            }.first()
        val session = decodeSession(preferences)
        if (preferences[KEY_STAGE] != null && session.stage == AuthSession.Stage.LoggedOut) {
            dataStore.edit { it.clear() }
            return AuthSession.LoggedOut
        }
        if (session.stage == AuthSession.Stage.Ready &&
            preferences[KEY_STAGE] == null &&
            hasValidTokenPair(preferences)
        ) {
            dataStore.edit {
                it[KEY_STAGE] = crypto.encrypt(AuthSession.Stage.Ready.name, KEY_STAGE.name)
            }
        }
        return session
    }

    override suspend fun compareAndSetStage(
        expected: AuthSession.Stage,
        next: AuthSession.Stage,
    ): Boolean {
        require(
            (expected == AuthSession.Stage.NeedsName && next == AuthSession.Stage.Welcome) ||
                (expected == AuthSession.Stage.Welcome && next == AuthSession.Stage.Ready),
        ) {
            "Illegal authenticated stage transition: $expected -> $next"
        }
        var changed = false
        dataStore.edit { prefs ->
            val current = decodeSession(prefs)
            if (current.stage != expected || current.userId == null) return@edit
            prefs[KEY_STAGE] = crypto.encrypt(next.name, KEY_STAGE.name)
            changed = true
        }
        return changed
    }

    override suspend fun saveStage(
        userId: Long,
        stage: AuthSession.Stage,
    ): Boolean {
        require(stage != AuthSession.Stage.LoggedOut)
        var changed = false
        dataStore.edit { prefs ->
            val current = decodeSession(prefs)
            if (current.userId != userId) return@edit
            if (current.stage == stage && prefs[KEY_STAGE] != null) {
                changed = true
                return@edit
            }
            if (current.stage != stage &&
                !(
                    current.stage == AuthSession.Stage.NeedsName &&
                        stage == AuthSession.Stage.Welcome ||
                        current.stage == AuthSession.Stage.Welcome &&
                        stage == AuthSession.Stage.Ready
                )
            ) {
                return@edit
            }
            prefs[KEY_STAGE] = crypto.encrypt(stage.name, KEY_STAGE.name)
            changed = true
        }
        return changed
    }

    override suspend fun getAccessToken(): String? = read(KEY_ACCESS_TOKEN)

    override suspend fun getRefreshToken(): String? = read(KEY_REFRESH_TOKEN)

    override suspend fun loadTokens(): TokenSnapshot? {
        val preferences = dataStore.data
            .catch { e ->
                if (e is IOException) emit(emptyPreferences()) else throw e
            }.first()
        val accessCiphertext = preferences[KEY_ACCESS_TOKEN] ?: return null
        val refreshCiphertext = preferences[KEY_REFRESH_TOKEN] ?: return null
        return try {
            TokenSnapshot(
                accessToken = crypto.decrypt(accessCiphertext, KEY_ACCESS_TOKEN.name),
                refreshToken = crypto.decrypt(refreshCiphertext, KEY_REFRESH_TOKEN.name),
            )
        } catch (e: Exception) {
            Timber.w(e, "Token snapshot decrypt failed; treating session as absent")
            null
        }
    }

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

    private fun hasValidTokenPair(preferences: Preferences): Boolean {
        val access = preferences[KEY_ACCESS_TOKEN] ?: return false
        val refresh = preferences[KEY_REFRESH_TOKEN] ?: return false
        return runCatching {
            crypto.decrypt(access, KEY_ACCESS_TOKEN.name)
            crypto.decrypt(refresh, KEY_REFRESH_TOKEN.name)
        }.isSuccess
    }

    private fun decodeSession(preferences: Preferences): AuthSession {
        if (!hasValidTokenPair(preferences)) return AuthSession.LoggedOut
        val userId = preferences[KEY_USER_ID]?.let {
            runCatching { crypto.decrypt(it, KEY_USER_ID.name).toLong() }.getOrNull()
        }
        val stageCiphertext = preferences[KEY_STAGE]
        if (stageCiphertext == null) {
            return AuthSession(userId = userId, stage = AuthSession.Stage.Ready)
        }
        return try {
            when (val stage = AuthSession.Stage.valueOf(crypto.decrypt(stageCiphertext, KEY_STAGE.name))) {
                AuthSession.Stage.LoggedOut -> AuthSession.LoggedOut
                else -> AuthSession(userId = userId, stage = stage)
            }
        } catch (error: Exception) {
            Timber.w(error, "Auth session stage decrypt failed; treating session as absent")
            AuthSession.LoggedOut
        }
    }

    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_STAGE = stringPreferencesKey("stage")
    }
}
