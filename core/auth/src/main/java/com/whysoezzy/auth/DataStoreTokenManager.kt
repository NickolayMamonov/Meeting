package com.whysoezzy.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.whysoezzy.auth.domain.models.AuthSession
import com.whysoezzy.auth.domain.models.CredentialVersion
import com.whysoezzy.network.TokenSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID

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

    override val credentialVersion: Flow<CredentialVersion> =
        resolvedState
            .map { it.credentialVersion }
            .distinctUntilChanged()

    override suspend fun saveTokens(
        accessToken: String,
        refreshToken: String,
        userId: Long?,
    ) {
        dataStore.edit { preferences ->
            val current = resolve(preferences)
            val version = ensureCredentialVersion(preferences)
            if (current.shouldClear && current.hasAnyAuthKey) {
                clearAuthKeys(preferences)
            }
            val resolvedUserId = userId ?: current.session.userId
            if (resolvedUserId == null) {
                clearAuthKeys(preferences)
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
            writeCredentialVersion(preferences, advance(version))
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
            val version = advance(ensureCredentialVersion(preferences))
            clearAuthKeys(preferences)
            preferences[KEY_ACCESS_TOKEN] = crypto.encrypt(accessToken, KEY_ACCESS_TOKEN.name)
            preferences[KEY_REFRESH_TOKEN] = crypto.encrypt(refreshToken, KEY_REFRESH_TOKEN.name)
            preferences[KEY_USER_ID] = crypto.encrypt(userId.toString(), KEY_USER_ID.name)
            preferences[KEY_STAGE] = crypto.encrypt(stage.name, KEY_STAGE.name)
            writeCredentialVersion(preferences, version)
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
        dataStore.edit {
            val version = ensureCredentialVersion(it)
            clearAuthKeys(it)
            writeCredentialVersion(it, version)
        }
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
            state.shouldClear -> clearAuthKeys(preferences)
            state.needsLegacyMigration ->
                preferences[KEY_STAGE] =
                    crypto.encrypt(AuthSession.Stage.Ready.name, KEY_STAGE.name)
        }
    }

    private fun resolve(preferences: Preferences): ResolvedState {
        val hasAnyAuthKey = AUTH_KEYS.any { preferences[it] != null }
        val credentialVersion = readCredentialVersion(preferences)
        if (!hasAnyAuthKey) {
            return ResolvedState(
                session = AuthSession.LoggedOut,
                credentialVersion = credentialVersion,
            )
        }

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
                credentialVersion = credentialVersion,
            )
        }

        val session = AuthSession(userId, stage)
        return ResolvedState(
            session = session,
            decryptedSession = DecryptedSession(access, refresh, userId),
            needsLegacyMigration = stageCiphertext == null,
            hasAnyAuthKey = hasAnyAuthKey,
            credentialVersion = credentialVersion,
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
        val credentialVersion: CredentialVersion = CredentialVersion("legacy", 0L),
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
        private val KEY_CREDENTIAL_EPOCH = stringPreferencesKey("credential_epoch")
        private val KEY_CREDENTIAL_REVISION = stringPreferencesKey("credential_revision")
        private val AUTH_KEYS = setOf(KEY_ACCESS_TOKEN, KEY_REFRESH_TOKEN, KEY_USER_ID, KEY_STAGE)
    }

    private fun ensureCredentialVersion(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
    ): CredentialVersion {
        val current = readCredentialVersion(preferences)
        if (preferences[KEY_CREDENTIAL_EPOCH] == null) {
            val initialized = CredentialVersion(UUID.randomUUID().toString(), 0L)
            writeCredentialVersion(preferences, initialized)
            return initialized
        }
        return current
    }

    private fun readCredentialVersion(preferences: Preferences): CredentialVersion {
        val epoch = preferences[KEY_CREDENTIAL_EPOCH]?.decrypt(KEY_CREDENTIAL_EPOCH.name)
        val revision = preferences[KEY_CREDENTIAL_REVISION]
            ?.decrypt(KEY_CREDENTIAL_REVISION.name)
            ?.toLongOrNull()
        return if (epoch.isNullOrBlank() || revision == null || revision < 0L) {
            CredentialVersion("legacy", 0L)
        } else {
            CredentialVersion(epoch, revision)
        }
    }

    private fun writeCredentialVersion(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        version: CredentialVersion,
    ) {
        preferences[KEY_CREDENTIAL_EPOCH] =
            crypto.encrypt(version.epoch, KEY_CREDENTIAL_EPOCH.name)
        preferences[KEY_CREDENTIAL_REVISION] =
            crypto.encrypt(version.revision.toString(), KEY_CREDENTIAL_REVISION.name)
    }

    private fun advance(version: CredentialVersion): CredentialVersion =
        if (version.revision == Long.MAX_VALUE) {
            CredentialVersion(UUID.randomUUID().toString(), 1L)
        } else {
            CredentialVersion(version.epoch, version.revision + 1L)
        }

    private fun clearAuthKeys(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
    ) {
        AUTH_KEYS.forEach(preferences::remove)
    }
}
