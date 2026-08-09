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
import kotlinx.coroutines.flow.map
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

    private val resolvedState: Flow<ResolvedState> =
        dataStore.data
            .catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }.map(::resolve)
            .distinctUntilChanged()

    override val isLoggedInFlow: Flow<Boolean> =
        resolvedState
            .map { it.session.stage != AuthSession.Stage.LoggedOut }
            .distinctUntilChanged()

    override val session: Flow<AuthSession> =
        resolvedState
            .map { if (it.shouldClear) AuthSession.LoggedOut else it.session }
            .distinctUntilChanged()

    override suspend fun saveTokens(
        accessToken: String,
        refreshToken: String,
        userId: Long?,
    ) {
        dataStore.edit { preferences ->
            val current = resolve(preferences)
            if (current.shouldClear && current.hasAnyAuthKey) {
                preferences.clear()
            }
            val resolvedUserId = userId ?: current.session.userId
            if (resolvedUserId == null) {
                preferences.clear()
                preferences[KEY_ACCESS_TOKEN] =
                    crypto.encrypt(accessToken, KEY_ACCESS_TOKEN.name)
                preferences[KEY_REFRESH_TOKEN] =
                    crypto.encrypt(refreshToken, KEY_REFRESH_TOKEN.name)
                return@edit
            }
            preferences[KEY_ACCESS_TOKEN] = crypto.encrypt(accessToken, KEY_ACCESS_TOKEN.name)
            preferences[KEY_REFRESH_TOKEN] = crypto.encrypt(refreshToken, KEY_REFRESH_TOKEN.name)
            preferences[KEY_USER_ID] = crypto.encrypt(resolvedUserId.toString(), KEY_USER_ID.name)
            if (current.needsLegacyMigration) {
                preferences[KEY_STAGE] =
                    crypto.encrypt(AuthSession.Stage.Ready.name, KEY_STAGE.name)
            }
        }
    }

    override suspend fun saveAuthenticated(
        accessToken: String,
        refreshToken: String,
        userId: Long,
        stage: AuthSession.Stage,
    ) {
        require(stage != AuthSession.Stage.LoggedOut)
        dataStore.edit { preferences ->
            preferences.clear()
            preferences[KEY_ACCESS_TOKEN] = crypto.encrypt(accessToken, KEY_ACCESS_TOKEN.name)
            preferences[KEY_REFRESH_TOKEN] = crypto.encrypt(refreshToken, KEY_REFRESH_TOKEN.name)
            preferences[KEY_USER_ID] = crypto.encrypt(userId.toString(), KEY_USER_ID.name)
            preferences[KEY_STAGE] = crypto.encrypt(stage.name, KEY_STAGE.name)
        }
    }

    override suspend fun readSession(): AuthSession {
        var state = ResolvedState(AuthSession.LoggedOut)
        dataStore.edit { preferences ->
            state = resolve(preferences)
            repair(preferences, state)
        }
        return if (state.shouldClear) AuthSession.LoggedOut else state.session
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
        dataStore.edit { preferences ->
            val state = resolve(preferences)
            if (!state.isValid || state.session.stage != expected) {
                repair(preferences, state)
                return@edit
            }
            preferences[KEY_STAGE] = crypto.encrypt(next.name, KEY_STAGE.name)
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
        dataStore.edit { preferences ->
            val state = resolve(preferences)
            if (!state.isValid || state.session.userId != userId) {
                repair(preferences, state)
                return@edit
            }
            if (state.session.stage == stage) {
                changed = true
                return@edit
            }
            if (!isAllowedTransition(state.session.stage, stage)) return@edit
            preferences[KEY_STAGE] = crypto.encrypt(stage.name, KEY_STAGE.name)
            changed = true
        }
        return changed
    }

    override suspend fun getAccessToken(): String? = coherentSnapshot()?.accessToken

    override suspend fun getRefreshToken(): String? = coherentSnapshot()?.refreshToken

    override suspend fun loadTokens(): TokenSnapshot? =
        coherentSnapshot()?.let { snapshot ->
            TokenSnapshot(snapshot.accessToken, snapshot.refreshToken)
        }

    override suspend fun getUserId(): Long? = coherentSnapshot()?.userId

    override suspend fun clearTokens() {
        dataStore.edit { it.clear() }
    }

    private suspend fun coherentSnapshot(): DecryptedSession? {
        var state = ResolvedState(AuthSession.LoggedOut)
        dataStore.edit { preferences ->
            state = resolve(preferences)
            repair(preferences, state)
        }
        return state.decryptedSession
    }

    private fun repair(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        state: ResolvedState,
    ) {
        when {
            state.shouldClear -> preferences.clear()
            state.needsLegacyMigration ->
                preferences[KEY_STAGE] =
                    crypto.encrypt(AuthSession.Stage.Ready.name, KEY_STAGE.name)
        }
    }

    private fun resolve(preferences: Preferences): ResolvedState {
        val hasAnyAuthKey = AUTH_KEYS.any { preferences[it] != null }
        if (!hasAnyAuthKey) return ResolvedState(AuthSession.LoggedOut)

        val access = preferences[KEY_ACCESS_TOKEN]?.decrypt(KEY_ACCESS_TOKEN.name)
        val refresh = preferences[KEY_REFRESH_TOKEN]?.decrypt(KEY_REFRESH_TOKEN.name)
        val userId = preferences[KEY_USER_ID]?.decrypt(KEY_USER_ID.name)?.toLongOrNull()
        val stageCiphertext = preferences[KEY_STAGE]
        val stage =
            if (stageCiphertext == null) {
                AuthSession.Stage.Ready.takeIf { access != null && refresh != null && userId != null }
            } else {
                stageCiphertext.decrypt(KEY_STAGE.name)?.let { value ->
                    runCatching { AuthSession.Stage.valueOf(value) }.getOrNull()
                }
            }

        if (access.isNullOrBlank() ||
            refresh.isNullOrBlank() ||
            userId == null ||
            stage == null ||
            stage == AuthSession.Stage.LoggedOut
        ) {
            return ResolvedState(
                session = AuthSession.LoggedOut,
                shouldClear = true,
                hasAnyAuthKey = hasAnyAuthKey,
            )
        }

        val session = AuthSession(userId, stage)
        return ResolvedState(
            session = session,
            decryptedSession = DecryptedSession(access, refresh, userId),
            needsLegacyMigration = stageCiphertext == null,
            hasAnyAuthKey = hasAnyAuthKey,
        )
    }

    private fun String.decrypt(aad: String): String? =
        runCatching { crypto.decrypt(this, aad) }.getOrNull()

    private fun isAllowedTransition(
        current: AuthSession.Stage,
        next: AuthSession.Stage,
    ): Boolean =
        (current == AuthSession.Stage.NeedsName && next == AuthSession.Stage.Welcome) ||
            (current == AuthSession.Stage.Welcome && next == AuthSession.Stage.Ready)

    private data class DecryptedSession(
        val accessToken: String,
        val refreshToken: String,
        val userId: Long,
    )

    private data class ResolvedState(
        val session: AuthSession,
        val decryptedSession: DecryptedSession? = null,
        val shouldClear: Boolean = false,
        val needsLegacyMigration: Boolean = false,
        val hasAnyAuthKey: Boolean = false,
    ) {
        val isValid: Boolean
            get() = decryptedSession != null && !shouldClear
    }

    private companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_STAGE = stringPreferencesKey("stage")
        private val AUTH_KEYS = setOf(KEY_ACCESS_TOKEN, KEY_REFRESH_TOKEN, KEY_USER_ID, KEY_STAGE)
    }
}
