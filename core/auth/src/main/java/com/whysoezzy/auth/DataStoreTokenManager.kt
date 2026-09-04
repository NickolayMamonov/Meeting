package com.whysoezzy.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.whysoezzy.auth.domain.models.AuthClearResult
import com.whysoezzy.auth.domain.models.AuthCredentialRead
import com.whysoezzy.auth.domain.models.AuthCredentialSnapshot
import com.whysoezzy.auth.domain.models.AuthCredentialState
import com.whysoezzy.auth.domain.models.AuthOperationPermit
import com.whysoezzy.auth.domain.models.AuthRefreshSaveResult
import com.whysoezzy.auth.domain.models.AuthSaveResult
import com.whysoezzy.auth.domain.models.AuthSession
import com.whysoezzy.auth.domain.models.ClearReservation
import com.whysoezzy.auth.domain.models.CredentialVersion
import com.whysoezzy.auth.domain.models.OwnerSaveReservation
import com.whysoezzy.auth.domain.models.PersistedTokenPair
import com.whysoezzy.network.TokenSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "secure_token_store",
)

internal class DataStoreTokenManager(
    context: Context,
) : TokenManager {
    private val appContext = context.applicationContext
    private val dataStore get() = appContext.tokenDataStore
    private val crypto = TokenCrypto(appContext)
    private val authMonitor = Any()

    private var authOwnerGeneration = 0L
    private var reservationSequence = 0L
    private var latestReservationId: Long? = null
    private var knownIdentity: com.whysoezzy.auth.domain.models.AuthCredentialIdentity? = null

    private val resolvedState: Flow<ResolvedState> =
        flow {
            readSession()
            emitAll(
                dataStore.data
                    .catch { error ->
                        if (error is IOException) emit(emptyPreferences()) else throw error
                    }.map(::resolve),
            )
        }.distinctUntilChanged()

    override val isLoggedInFlow: Flow<Boolean> =
        resolvedState
            .map { it.session.stage != AuthSession.Stage.LoggedOut }
            .distinctUntilChanged()

    override val session: Flow<AuthSession> =
        resolvedState
            .map { if (it.shouldClear) AuthSession.LoggedOut else it.session }
            .distinctUntilChanged()

    override val credentialState: Flow<AuthCredentialState> =
        resolvedState
            .map {
                AuthCredentialState(
                    session = if (it.shouldClear) AuthSession.LoggedOut else it.session,
                    credentialVersion = it.credentialVersion,
                )
            }.distinctUntilChanged()

    override val credentialVersion: Flow<CredentialVersion> =
        resolvedState
            .map { it.credentialVersion }
            .distinctUntilChanged()

    override fun captureAuthOperationPermit(): AuthOperationPermit =
        synchronized(authMonitor) {
            AuthOperationPermit(authOwnerGeneration, knownIdentity)
        }

    override suspend fun readCredentialSnapshot(
        permit: AuthOperationPermit,
    ): AuthCredentialRead {
        val identityBeforeRead = synchronized(authMonitor) { knownIdentity }
        var state = ResolvedState(AuthSession.LoggedOut)
        dataStore.edit { preferences ->
            state = resolve(preferences)
            repair(preferences, state)
            state = resolve(preferences)
        }

        return synchronized(authMonitor) {
            if (permit.generation != authOwnerGeneration ||
                (permit.identity != null && permit.identity != state.identity) ||
                knownIdentity != identityBeforeRead
            ) {
                AuthCredentialRead.Stale
            } else {
                val boundPermit = AuthOperationPermit(permit.generation, state.identity)
                knownIdentity = state.identity
                val decryptedSession = state.decryptedSession
                if (decryptedSession == null) {
                    AuthCredentialRead.Missing(boundPermit)
                } else {
                    AuthCredentialRead.Present(
                        AuthCredentialSnapshot(
                            accessToken = decryptedSession.accessToken,
                            refreshToken = decryptedSession.refreshToken,
                            userId = decryptedSession.userId,
                            stage = state.session.stage,
                            credentialVersion = state.credentialVersion,
                        ),
                        boundPermit,
                    )
                }
            }
        }
    }

    override fun reserveOwnerSave(): OwnerSaveReservation =
        synchronized(authMonitor) {
            val generation = advanceGenerationLocked()
            val reservation = OwnerSaveReservation(
                generation = generation,
                reservationId = nextReservationIdLocked(),
                clearPermit = AuthOperationPermit(generation, knownIdentity),
            )
            latestReservationId = reservation.reservationId
            reservation
        }

    override suspend fun saveAuthenticated(
        reservation: OwnerSaveReservation,
        accessToken: String,
        refreshToken: String,
        userId: Long,
        stage: AuthSession.Stage,
    ): AuthSaveResult {
        require(stage != AuthSession.Stage.LoggedOut)
        var persisted = false
        var persistedVersion: CredentialVersion? = null
        var writtenAccess: String? = null
        var writtenRefresh: String? = null
        var writtenUserId: String? = null
        dataStore.edit { preferences ->
            val current = synchronized(authMonitor) {
                reservation.generation == authOwnerGeneration &&
                    latestReservationId == reservation.reservationId
            }
            if (!current) return@edit

            val version = advance(ensureCredentialVersion(preferences))
            clearAuthKeys(preferences)
            writtenAccess = crypto.encrypt(accessToken, KEY_ACCESS_TOKEN.name)
            writtenRefresh = crypto.encrypt(refreshToken, KEY_REFRESH_TOKEN.name)
            writtenUserId = crypto.encrypt(userId.toString(), KEY_USER_ID.name)
            preferences[KEY_ACCESS_TOKEN] = requireNotNull(writtenAccess)
            preferences[KEY_REFRESH_TOKEN] = requireNotNull(writtenRefresh)
            preferences[KEY_USER_ID] = requireNotNull(writtenUserId)
            preferences[KEY_STAGE] = crypto.encrypt(stage.name, KEY_STAGE.name)
            writeCredentialVersion(preferences, version)
            persistedVersion = version
            persisted = true
        }

        if (persisted) {
            val stillCurrent = synchronized(authMonitor) {
                reservation.generation == authOwnerGeneration &&
                    latestReservationId == reservation.reservationId
            }
            if (!stillCurrent) {
                dataStore.edit { preferences ->
                    if (preferences[KEY_ACCESS_TOKEN] == writtenAccess &&
                        preferences[KEY_REFRESH_TOKEN] == writtenRefresh &&
                        preferences[KEY_USER_ID] == writtenUserId
                    ) {
                        clearAuthKeys(preferences)
                    }
                }
                return AuthSaveResult.StaleSkipped
            }
            synchronized(authMonitor) {
                knownIdentity =
                    com.whysoezzy.auth.domain.models.AuthCredentialIdentity(
                        userId,
                        stage,
                        requireNotNull(persistedVersion),
                        refreshToken,
                    )
                latestReservationId = null
            }
            return AuthSaveResult.Persisted
        }
        return AuthSaveResult.StaleSkipped
    }

    override suspend fun saveRefreshedTokens(
        permit: AuthOperationPermit,
        accessToken: String,
        refreshToken: String,
    ): AuthRefreshSaveResult {
        var persisted = false
        var persistedVersion: CredentialVersion? = null
        var writtenAccess: String? = null
        var writtenRefresh: String? = null
        try {
            dataStore.edit { preferences ->
                val state = resolve(preferences)
                val current = synchronized(authMonitor) {
                    permit.generation == authOwnerGeneration &&
                        permit.identity != null &&
                        permit.identity == state.identity
                }
                if (!current) return@edit

                val version = advance(ensureCredentialVersion(preferences))
                writtenAccess = crypto.encrypt(accessToken, KEY_ACCESS_TOKEN.name)
                writtenRefresh = crypto.encrypt(refreshToken, KEY_REFRESH_TOKEN.name)
                preferences[KEY_ACCESS_TOKEN] = requireNotNull(writtenAccess)
                preferences[KEY_REFRESH_TOKEN] = requireNotNull(writtenRefresh)
                writeCredentialVersion(preferences, version)
                persistedVersion = version
                persisted = true
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return AuthRefreshSaveResult.Failed(error)
        }

        if (!persisted) return AuthRefreshSaveResult.StaleSkipped
        val version = requireNotNull(persistedVersion)
        val stillCurrent = synchronized(authMonitor) {
            permit.generation == authOwnerGeneration &&
                latestReservationId == null
        }
        if (!stillCurrent) {
            dataStore.edit { preferences ->
                if (preferences[KEY_ACCESS_TOKEN] == writtenAccess &&
                    preferences[KEY_REFRESH_TOKEN] == writtenRefresh
                ) {
                    preferences.remove(KEY_ACCESS_TOKEN)
                    preferences.remove(KEY_REFRESH_TOKEN)
                }
            }
            return AuthRefreshSaveResult.StaleSkipped
        }
        synchronized(authMonitor) {
            knownIdentity = requireNotNull(permit.identity).copy(
                credentialVersion = version,
                refreshToken = refreshToken,
            )
        }
        return AuthRefreshSaveResult.Persisted(
            PersistedTokenPair(accessToken, refreshToken),
        )
    }

    override fun reserveClear(permit: AuthOperationPermit): ClearReservation? =
        synchronized(authMonitor) {
            if (permit.generation != authOwnerGeneration || permit.identity != knownIdentity) {
                return@synchronized null
            }
            val generation = advanceGenerationLocked()
            val reservation = ClearReservation(
                generation = generation,
                reservationId = nextReservationIdLocked(),
                identity = permit.identity,
            )
            latestReservationId = reservation.reservationId
            knownIdentity = null
            reservation
        }

    override suspend fun clearReserved(reservation: ClearReservation): AuthClearResult {
        var cleared = false
        dataStore.edit { preferences ->
            val current = synchronized(authMonitor) {
                reservation.generation == authOwnerGeneration &&
                    latestReservationId == reservation.reservationId
            }
            if (!current) return@edit

            val state = resolve(preferences)
            val identityMatches = when (val expected = reservation.identity) {
                null -> state.decryptedSession == null
                else -> state.identity == expected
            }
            if (!identityMatches) return@edit

            ensureCredentialVersion(preferences)
            clearAuthKeys(preferences)
            cleared = true
        }
        if (cleared) {
            synchronized(authMonitor) {
                if (reservation.generation == authOwnerGeneration &&
                    latestReservationId == reservation.reservationId
                ) {
                    knownIdentity = null
                }
            }
            return AuthClearResult.Cleared
        }
        return AuthClearResult.StaleSkipped
    }

    override suspend fun saveTokens(
        accessToken: String,
        refreshToken: String,
        userId: Long?,
    ) {
        val permit = captureAuthOperationPermit()
        when (val read = readCredentialSnapshot(permit)) {
            is AuthCredentialRead.Present -> {
                val result = saveRefreshedTokens(read.permit, accessToken, refreshToken)
                if (result is AuthRefreshSaveResult.Failed) throw result.error
            }
            is AuthCredentialRead.Missing,
            AuthCredentialRead.Stale,
            -> {
                val reservation = reserveOwnerSave()
                val resolvedUserId = userId ?: return
                saveAuthenticated(
                    reservation,
                    accessToken,
                    refreshToken,
                    resolvedUserId,
                    AuthSession.Stage.Ready,
                )
            }
        }
    }

    override suspend fun saveAuthenticated(
        accessToken: String,
        refreshToken: String,
        userId: Long,
        stage: AuthSession.Stage,
    ) {
        val reservation = reserveOwnerSave()
        saveAuthenticated(reservation, accessToken, refreshToken, userId, stage)
    }

    override suspend fun readSession(): AuthSession {
        val observed = synchronized(authMonitor) {
            authOwnerGeneration to knownIdentity
        }
        var state = ResolvedState(AuthSession.LoggedOut)
        dataStore.edit { preferences ->
            state = resolve(preferences)
            repair(preferences, state)
            state = resolve(preferences)
        }
        synchronized(authMonitor) {
            if (authOwnerGeneration == observed.first && knownIdentity == observed.second) {
                knownIdentity = state.identity
            }
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
        if (changed) {
            synchronized(authMonitor) {
                if (knownIdentity?.stage == expected) {
                    knownIdentity = knownIdentity?.copy(stage = next)
                }
            }
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
        if (changed) {
            synchronized(authMonitor) {
                if (knownIdentity?.userId == userId) {
                    knownIdentity = knownIdentity?.copy(stage = stage)
                }
            }
        }
        return changed
    }

    override suspend fun getAccessToken(): String? =
        readCredentialSnapshot(captureAuthOperationPermit())
            .let { (it as? AuthCredentialRead.Present)?.snapshot?.accessToken }

    override suspend fun getRefreshToken(): String? =
        readCredentialSnapshot(captureAuthOperationPermit())
            .let { (it as? AuthCredentialRead.Present)?.snapshot?.refreshToken }

    override suspend fun loadTokens(): TokenSnapshot? =
        readCredentialSnapshot(captureAuthOperationPermit())
            .let { (it as? AuthCredentialRead.Present)?.snapshot }
            ?.let { TokenSnapshot(it.accessToken, it.refreshToken) }

    override suspend fun getUserId(): Long? =
        readCredentialSnapshot(captureAuthOperationPermit())
            .let { (it as? AuthCredentialRead.Present)?.snapshot?.userId }

    override suspend fun clearTokens() {
        val read = readCredentialSnapshot(captureAuthOperationPermit())
        val permit = when (read) {
            is AuthCredentialRead.Present -> read.permit
            is AuthCredentialRead.Missing -> read.permit
            AuthCredentialRead.Stale -> return
        }
        val reservation = reserveClear(permit) ?: return
        clearReserved(reservation)
    }

    private fun repair(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        state: ResolvedState,
    ) {
        when {
            state.corruptCredentialMetadata -> {
                clearAuthKeys(preferences)
                writeCredentialVersion(preferences, newCredentialVersion(0L))
            }
            state.needsCredentialMigration -> {
                writeCredentialVersion(preferences, newCredentialVersion(1L))
                if (state.needsLegacyStageMigration) {
                    preferences[KEY_STAGE] =
                        crypto.encrypt(AuthSession.Stage.Ready.name, KEY_STAGE.name)
                }
            }
            state.needsCredentialInitialization -> {
                if (state.shouldClear) clearAuthKeys(preferences)
                writeCredentialVersion(preferences, newCredentialVersion(0L))
            }
            state.shouldClear -> clearAuthKeys(preferences)
            state.needsLegacyStageMigration ->
                preferences[KEY_STAGE] =
                    crypto.encrypt(AuthSession.Stage.Ready.name, KEY_STAGE.name)
        }
    }

    private fun resolve(preferences: Preferences): ResolvedState {
        val hasAnyAuthKey = AUTH_KEYS.any { preferences[it] != null }
        val credentialMetadata = readCredentialMetadata(preferences)
        val credentialVersion = credentialMetadata.version ?: CredentialVersion("legacy", 0L)
        if (!hasAnyAuthKey) {
            return ResolvedState(
                session = AuthSession.LoggedOut,
                credentialVersion = credentialVersion,
                needsCredentialInitialization = credentialMetadata is CredentialMetadata.Missing,
                corruptCredentialMetadata = credentialMetadata is CredentialMetadata.Corrupt,
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
            stage == AuthSession.Stage.LoggedOut ||
            credentialMetadata is CredentialMetadata.Corrupt
        ) {
            return ResolvedState(
                session = AuthSession.LoggedOut,
                shouldClear = true,
                hasAnyAuthKey = hasAnyAuthKey,
                credentialVersion = credentialVersion,
                corruptCredentialMetadata = credentialMetadata is CredentialMetadata.Corrupt,
                needsCredentialInitialization = credentialMetadata is CredentialMetadata.Missing,
            )
        }

        return ResolvedState(
            session = AuthSession(userId, stage),
            decryptedSession = DecryptedSession(access, refresh, userId),
            needsLegacyStageMigration = stageCiphertext == null,
            needsCredentialMigration = credentialMetadata is CredentialMetadata.Missing,
            hasAnyAuthKey = hasAnyAuthKey,
            credentialVersion = credentialVersion,
        )
    }

    private val ResolvedState.identity
        get() = decryptedSession?.let {
            com.whysoezzy.auth.domain.models.AuthCredentialIdentity(
                userId = it.userId,
                stage = session.stage,
                credentialVersion = credentialVersion,
                refreshToken = it.refreshToken,
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
        val needsLegacyStageMigration: Boolean = false,
        val needsCredentialMigration: Boolean = false,
        val needsCredentialInitialization: Boolean = false,
        val corruptCredentialMetadata: Boolean = false,
        val hasAnyAuthKey: Boolean = false,
    ) {
        val isValid: Boolean
            get() = decryptedSession != null && !shouldClear
    }

    private fun advanceGenerationLocked(): Long {
        check(authOwnerGeneration != Long.MAX_VALUE) { "Auth owner generation exhausted" }
        authOwnerGeneration += 1L
        return authOwnerGeneration
    }

    private fun nextReservationIdLocked(): Long {
        check(reservationSequence != Long.MAX_VALUE) { "Auth reservation id exhausted" }
        reservationSequence += 1L
        return reservationSequence
    }

    private fun ensureCredentialVersion(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
    ): CredentialVersion =
        when (val metadata = readCredentialMetadata(preferences)) {
            is CredentialMetadata.Valid -> metadata.value
            CredentialMetadata.Missing,
            CredentialMetadata.Corrupt,
            -> newCredentialVersion(0L).also { writeCredentialVersion(preferences, it) }
        }

    private fun readCredentialMetadata(preferences: Preferences): CredentialMetadata {
        val epochCiphertext = preferences[KEY_CREDENTIAL_EPOCH]
        val revisionCiphertext = preferences[KEY_CREDENTIAL_REVISION]
        if (epochCiphertext == null && revisionCiphertext == null) return CredentialMetadata.Missing
        if (epochCiphertext == null || revisionCiphertext == null) return CredentialMetadata.Corrupt
        val epoch = epochCiphertext.decrypt(KEY_CREDENTIAL_EPOCH.name)
        val revision = revisionCiphertext.decrypt(KEY_CREDENTIAL_REVISION.name)?.toLongOrNull()
        if (epoch.isNullOrBlank() || revision == null || revision < 0L) {
            return CredentialMetadata.Corrupt
        }
        return runCatching {
            UUID.fromString(epoch)
            CredentialMetadata.Valid(CredentialVersion(epoch, revision))
        }.getOrElse { CredentialMetadata.Corrupt }
    }

    private fun newCredentialVersion(revision: Long): CredentialVersion =
        CredentialVersion(UUID.randomUUID().toString(), revision)

    private fun advance(version: CredentialVersion): CredentialVersion =
        if (version.revision == Long.MAX_VALUE) {
            CredentialVersion(UUID.randomUUID().toString(), 1L)
        } else {
            CredentialVersion(version.epoch, version.revision + 1L)
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

    private fun clearAuthKeys(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
    ) {
        AUTH_KEYS.forEach(preferences::remove)
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

    private sealed interface CredentialMetadata {
        data class Valid(
            val value: CredentialVersion,
        ) : CredentialMetadata

        data object Missing : CredentialMetadata

        data object Corrupt : CredentialMetadata
    }

    private val CredentialMetadata.version: CredentialVersion?
        get() = (this as? CredentialMetadata.Valid)?.value
}
