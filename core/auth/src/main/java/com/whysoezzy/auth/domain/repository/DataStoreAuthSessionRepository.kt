package com.whysoezzy.auth.domain.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.whysoezzy.auth.TokenCrypto
import com.whysoezzy.auth.domain.models.AuthSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException

private val Context.authSessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "secure_auth_session_store",
)

class DataStoreAuthSessionRepository(
    context: Context,
) : AuthSessionRepository {
    private val appContext = context.applicationContext
    private val dataStore get() = appContext.authSessionDataStore
    private val crypto = TokenCrypto(appContext)

    override val session: Flow<AuthSession> =
        dataStore.data
            .catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }.map(::decode)
            .distinctUntilChanged()

    override suspend fun read(): AuthSession =
        dataStore.data
            .catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }.map(::decode)
            .first()

    override suspend fun saveAuthenticated(
        userId: Long,
        stage: AuthSession.Stage,
    ) {
        require(stage != AuthSession.Stage.LoggedOut) {
            "Authenticated sessions cannot be saved as LoggedOut"
        }
        dataStore.edit { preferences ->
            val current = decode(preferences)
            if (current.stage.isAfter(stage)) {
                return@edit
            }
            preferences[USER_ID] = crypto.encrypt(userId.toString(), USER_ID.name)
            preferences[STAGE] = crypto.encrypt(stage.name, STAGE.name)
        }
    }

    override suspend fun compareAndSetStage(
        expected: AuthSession.Stage,
        next: AuthSession.Stage,
    ): Boolean {
        var changed = false
        dataStore.edit { preferences ->
            val current = decode(preferences)
            if (current.stage != expected) return@edit
            if (current.stage.isAfter(next)) return@edit

            if (next == AuthSession.Stage.LoggedOut) {
                preferences.remove(USER_ID)
                preferences.remove(STAGE)
            } else {
                val userId = current.userId ?: return@edit
                preferences[USER_ID] = crypto.encrypt(userId.toString(), USER_ID.name)
                preferences[STAGE] = crypto.encrypt(next.name, STAGE.name)
            }
            changed = true
        }
        return changed
    }

    override suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(USER_ID)
            preferences.remove(STAGE)
        }
    }

    private fun decode(preferences: Preferences): AuthSession {
        val userIdCiphertext = preferences[USER_ID] ?: return AuthSession.LoggedOut
        val stageCiphertext = preferences[STAGE] ?: return AuthSession.LoggedOut
        return try {
            val userId = crypto.decrypt(userIdCiphertext, USER_ID.name).toLong()
            val stage = AuthSession.Stage.valueOf(crypto.decrypt(stageCiphertext, STAGE.name))
            if (stage == AuthSession.Stage.LoggedOut) {
                AuthSession.LoggedOut
            } else {
                AuthSession(userId = userId, stage = stage)
            }
        } catch (error: Exception) {
            Timber.w(error, "Auth session decrypt failed; treating session as absent")
            AuthSession.LoggedOut
        }
    }

    private companion object {
        val USER_ID = stringPreferencesKey("user_id")
        val STAGE = stringPreferencesKey("stage")
    }
}

private fun AuthSession.Stage.isAfter(other: AuthSession.Stage): Boolean =
    when (this) {
        AuthSession.Stage.LoggedOut -> false
        AuthSession.Stage.NeedsName ->
            other == AuthSession.Stage.LoggedOut
        AuthSession.Stage.Welcome ->
            other == AuthSession.Stage.LoggedOut ||
                other == AuthSession.Stage.NeedsName
        AuthSession.Stage.Ready ->
            other != AuthSession.Stage.Ready
    }
