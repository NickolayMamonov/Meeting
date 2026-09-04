package com.whysoezzy.auth

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.whysoezzy.auth.domain.models.AuthOutcome
import com.whysoezzy.auth.domain.models.AuthClearResult
import com.whysoezzy.auth.domain.models.AuthCredentialRead
import com.whysoezzy.auth.domain.models.AuthRefreshSaveResult
import com.whysoezzy.auth.domain.models.AuthSaveResult
import com.whysoezzy.auth.domain.models.AuthSession
import com.whysoezzy.auth.domain.models.DispatchOutcome
import com.whysoezzy.auth.domain.models.EmailAddress
import com.whysoezzy.auth.domain.models.EmailAddressParser
import com.whysoezzy.auth.domain.repository.DataStoreAuthSessionRepository
import com.whysoezzy.auth.domain.repository.DataStorePendingEmailOtpStore
import com.whysoezzy.auth.domain.repository.PendingEmailOtpAttempt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AuthPersistenceInstrumentationTest {
    private lateinit var context: IsolatedStorageContext
    private lateinit var tokenManager: DataStoreTokenManager
    private lateinit var pendingStore: DataStorePendingEmailOtpStore

    @Before
    fun setUp() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        context = IsolatedStorageContext(targetContext, "real_store_contract")
        context.reset()
        tokenManager = DataStoreTokenManager(context)
        pendingStore = DataStorePendingEmailOtpStore(context)
    }

    @After
    fun tearDown() = runBlocking {
        // Clear through the real APIs so no authentication material survives this test run.
        runCatching { tokenManager.clearTokens() }
        runCatching { pendingStore.clearActive() }
        context.clearIsolatedSharedPreferences()
    }

    @Test
    fun realStores_verifyPersistenceContractWithoutTouchingInstalledAppData() = runBlocking {
        corruptEncryptedState_failsClosed()
        clearActive_unconditionallyRemovesCorruptPendingRecord()
        stageLessTokenOnlyState_failsClosed_andIsRemoved()
        allStores_encryptSensitiveValues_recreateAndCleanUp()
        legacyTokenOnlyState_canBeMigratedToReadyAndSurvivesRecreation()
        authSession_allowsForwardCasAndIdentityReplacement_butRejectsStaleTransitions()
        pendingStore_replacesIdentityAndGenerationCheckedCleanupDoesNotDeleteNewAttempt()
    }

    private suspend fun allStores_encryptSensitiveValues_recreateAndCleanUp() {
        val accessToken = "access-token-persistence-probe"
        val refreshToken = "refresh-token-persistence-probe"
        val userId = 9_876_543_210L
        val attempt = pendingAttempt(
            attemptId = "attempt-persistence-probe",
            email = email("sensitive.persistence@example.com"),
        )

        tokenManager.saveAuthenticated(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = userId,
            stage = AuthSession.Stage.NeedsName,
        )
        pendingStore.replace(attempt)

        val persistedBytes = context.persistedBytes()
        listOf(
            accessToken,
            refreshToken,
            userId.toString(),
            attempt.attemptId,
            "sensitive.persistence@example.com",
            AuthSession.Stage.NeedsName.name,
        ).forEach { plaintext ->
            assertFalse(
                "Sensitive plaintext was persisted: $plaintext",
                persistedBytes.containsSequence(plaintext.encodeToByteArray()),
            )
        }

        val recreatedTokens = tokenManager
        val recreatedSession = DataStoreAuthSessionRepository(recreatedTokens)
        val recreatedPending = pendingStore
        assertEquals(accessToken, recreatedTokens.getAccessToken())
        assertEquals(refreshToken, recreatedTokens.getRefreshToken())
        assertEquals(userId, recreatedTokens.getUserId())
        assertEquals(
            AuthSession(userId, AuthSession.Stage.NeedsName),
            recreatedSession.read(),
        )
        assertEquals(attempt, recreatedPending.getActive())

        recreatedTokens.clearTokens()
        recreatedSession.clear()
        recreatedPending.clearActive()

        assertNull(tokenManager.loadTokens())
        val clearedTokens = tokenManager
        assertEquals(AuthSession.LoggedOut, DataStoreAuthSessionRepository(clearedTokens).read())
        assertNull(pendingStore.getActive())
    }

    private suspend fun legacyTokenOnlyState_canBeMigratedToReadyAndSurvivesRecreation() {
        val legacyTokens = tokenManager
        legacyTokens.saveTokens("legacy-access", "legacy-refresh", 42L)

        // Reading a valid install that predates the stage key promotes it to Ready and
        // persists the migration into the same encrypted token transaction.
        assertEquals(
            AuthSession(42L, AuthSession.Stage.Ready),
            DataStoreAuthSessionRepository(legacyTokens).read(),
        )

        val recreatedTokens = tokenManager
        assertEquals(
            AuthSession(42L, AuthSession.Stage.Ready),
            DataStoreAuthSessionRepository(recreatedTokens).read(),
        )
        assertEquals("legacy-access", recreatedTokens.getAccessToken())
        assertFalse(
            context.persistedBytes().containsSequence(
                AuthSession.Stage.Ready.name
                    .encodeToByteArray(),
            ),
        )
    }

    @Test
    fun legacyCredentialMetadata_migratesAtomically_andSurvivesClear() = runBlocking {
        val crypto = TokenCrypto(context)
        writeRawPreferences(TOKEN_STORE) {
            it[stringPreferencesKey("access_token")] =
                crypto.encrypt("legacy-access", "access_token")
            it[stringPreferencesKey("refresh_token")] =
                crypto.encrypt("legacy-refresh", "refresh_token")
            it[stringPreferencesKey("user_id")] =
                crypto.encrypt("42", "user_id")
            it[stringPreferencesKey("stage")] =
                crypto.encrypt(AuthSession.Stage.Ready.name, "stage")
        }

        assertEquals(AuthSession(42L, AuthSession.Stage.Ready), tokenManager.readSession())
        val migrated = tokenManager.credentialVersion.first()
        assertTrue(migrated.epoch != "legacy")
        assertEquals(1L, migrated.revision)

        tokenManager.clearTokens()
        assertEquals(migrated, tokenManager.credentialVersion.first())
    }

    @Test
    fun corruptCredentialMetadata_failsClosed() = runBlocking {
        val crypto = TokenCrypto(context)
        writeRawPreferences(TOKEN_STORE) {
            it[stringPreferencesKey("access_token")] =
                crypto.encrypt("corrupt-access", "access_token")
            it[stringPreferencesKey("refresh_token")] =
                crypto.encrypt("corrupt-refresh", "refresh_token")
            it[stringPreferencesKey("user_id")] =
                crypto.encrypt("42", "user_id")
            it[stringPreferencesKey("stage")] =
                crypto.encrypt(AuthSession.Stage.Ready.name, "stage")
            it[stringPreferencesKey("credential_epoch")] =
                crypto.encrypt("not-a-uuid", "credential_epoch")
            it[stringPreferencesKey("credential_revision")] =
                crypto.encrypt("0", "credential_revision")
        }

        assertEquals(AuthSession.LoggedOut, tokenManager.readSession())
        assertNull(tokenManager.loadTokens())
        val repaired = tokenManager.credentialVersion.first()
        assertTrue(repaired.epoch != "legacy")
        assertEquals(0L, repaired.revision)

        tokenManager.saveAuthenticated(
            accessToken = "recovered-access",
            refreshToken = "recovered-refresh",
            userId = 42L,
            stage = AuthSession.Stage.Ready,
        )
        assertEquals(1L, tokenManager.credentialVersion.first().revision)
    }

    @Test
    fun malformedCredentialCiphertext_failsClosed() = runBlocking {
        val crypto = TokenCrypto(context)
        writeRawPreferences(TOKEN_STORE) {
            it[stringPreferencesKey("access_token")] =
                crypto.encrypt("access", "access_token")
            it[stringPreferencesKey("refresh_token")] =
                crypto.encrypt("refresh", "refresh_token")
            it[stringPreferencesKey("user_id")] =
                crypto.encrypt("42", "user_id")
            it[stringPreferencesKey("stage")] =
                crypto.encrypt(AuthSession.Stage.Ready.name, "stage")
            it[stringPreferencesKey("credential_epoch")] = "malformed-ciphertext"
            it[stringPreferencesKey("credential_revision")] = "also-malformed"
        }

        assertEquals(AuthSession.LoggedOut, tokenManager.readSession())
        assertNull(tokenManager.loadTokens())
        val repaired = tokenManager.credentialVersion.first()
        assertEquals(0L, repaired.revision)
        assertTrue(repaired.epoch != "legacy")

        tokenManager.saveAuthenticated(
            accessToken = "recovered-access",
            refreshToken = "recovered-refresh",
            userId = 42L,
            stage = AuthSession.Stage.Ready,
        )
        assertEquals(1L, tokenManager.credentialVersion.first().revision)
    }

    @Test
    fun generationBoundRefreshAndClear_preserveLatestOwner() = runBlocking {
        tokenManager.saveAuthenticated(
            accessToken = "owner-a-access",
            refreshToken = "owner-a-refresh",
            userId = 1L,
            stage = AuthSession.Stage.Ready,
        )
        val aRead = tokenManager.readCredentialSnapshot(
            tokenManager.captureAuthOperationPermit(),
        )
        val aPermit = (aRead as AuthCredentialRead.Present).permit

        val firstOwner = tokenManager.reserveOwnerSave()
        val latestOwner = tokenManager.reserveOwnerSave()
        assertEquals(
            AuthSaveResult.StaleSkipped,
            tokenManager.saveAuthenticated(
                firstOwner,
                "owner-b-stale-access",
                "owner-b-stale-refresh",
                2L,
                AuthSession.Stage.Ready,
            ),
        )
        assertEquals(
            AuthSaveResult.Persisted,
            tokenManager.saveAuthenticated(
                latestOwner,
                "owner-b-access",
                "owner-b-refresh",
                2L,
                AuthSession.Stage.Ready,
            ),
        )

        assertEquals(
            AuthRefreshSaveResult.StaleSkipped,
            tokenManager.saveRefreshedTokens(
                aPermit,
                "owner-a-late-access",
                "owner-a-late-refresh",
            ),
        )
        assertNull(tokenManager.reserveClear(aPermit))

        val bRead = tokenManager.readCredentialSnapshot(
            tokenManager.captureAuthOperationPermit(),
        )
        val bPresent = bRead as AuthCredentialRead.Present
        val bSnapshot = bPresent.snapshot
        assertEquals(2L, bSnapshot.userId)
        assertEquals("owner-b-access", bSnapshot.accessToken)
        assertEquals("owner-b-refresh", bSnapshot.refreshToken)
        val versionBeforeClear = bSnapshot.credentialVersion

        val clearReservation = requireNotNull(
            tokenManager.reserveClear(bPresent.permit),
        )
        assertEquals(
            AuthClearResult.Cleared,
            tokenManager.clearReserved(clearReservation),
        )
        assertEquals(versionBeforeClear, tokenManager.credentialVersion.first())
        assertNull(tokenManager.loadTokens())
    }

    private suspend fun authSession_allowsForwardCasAndIdentityReplacement_butRejectsStaleTransitions() {
        val repository = DataStoreAuthSessionRepository(tokenManager)
        tokenManager.saveAuthenticated(
            accessToken = "first-access",
            refreshToken = "first-refresh",
            userId = 100L,
            stage = AuthSession.Stage.NeedsName,
        )

        assertTrue(
            repository.compareAndSetStage(
                AuthSession.Stage.NeedsName,
                AuthSession.Stage.Welcome,
            ),
        )
        assertFalse(
            repository.compareAndSetStage(
                AuthSession.Stage.NeedsName,
                AuthSession.Stage.Welcome,
            ),
        )
        assertTrue(
            repository.compareAndSetStage(
                AuthSession.Stage.Welcome,
                AuthSession.Stage.Ready,
            ),
        )

        tokenManager.saveAuthenticated(
            accessToken = "replacement-access",
            refreshToken = "replacement-refresh",
            userId = 200L,
            stage = AuthSession.Stage.Ready,
        )
        assertEquals(
            AuthSession(200L, AuthSession.Stage.Ready),
            DataStoreAuthSessionRepository(tokenManager).read(),
        )
        assertEquals("replacement-access", tokenManager.getAccessToken())
    }

    private suspend fun corruptEncryptedState_failsClosed() {
        val marker = "not-valid-tink-ciphertext"
        writeRawPreferences(TOKEN_STORE) {
            it[stringPreferencesKey("access_token")] = marker
            it[stringPreferencesKey("refresh_token")] = marker
            it[stringPreferencesKey("user_id")] = marker
            it[stringPreferencesKey("stage")] = marker
        }
        val tokens = tokenManager
        assertFalse(tokens.isLoggedInFlow.first())
        assertEquals(AuthSession.LoggedOut, DataStoreAuthSessionRepository(tokens).read())
        assertNull(tokens.loadTokens())
        assertNull(tokens.getAccessToken())
        assertNull(tokens.getRefreshToken())
        assertNull(tokens.getUserId())
        val tokenStoreBytes =
            context.dataStoreFile(TOKEN_STORE).takeIf(File::exists)?.readBytes() ?: ByteArray(0)
        assertFalse(
            "Corrupt token/session record was not cleaned up",
            tokenStoreBytes.containsSequence(marker.encodeToByteArray()),
        )
    }

    private suspend fun stageLessTokenOnlyState_failsClosed_andIsRemoved() {
        val tokens = tokenManager
        tokens.saveTokens("orphan-access", "orphan-refresh", userId = null)
        assertFalse(tokens.isLoggedInFlow.first())
        assertEquals(AuthSession.LoggedOut, tokens.readSession())
        assertNull(tokens.loadTokens())
        val tokenStoreBytes =
            context.dataStoreFile(TOKEN_STORE).takeIf(File::exists)?.readBytes() ?: ByteArray(0)
        assertFalse(tokenStoreBytes.containsSequence("orphan-access".encodeToByteArray()))
    }

    private suspend fun clearActive_unconditionallyRemovesCorruptPendingRecord() {
        val marker = "corrupt-pending-record-for-clear-active"
        writeRawPreferences(PENDING_STORE) {
            it[stringPreferencesKey("pending_email_otp_record")] = marker
        }

        pendingStore.clearActive()

        val pendingStoreBytes =
            context.dataStoreFile(PENDING_STORE).takeIf(File::exists)?.readBytes() ?: ByteArray(0)
        assertFalse(
            "Unqualified clearActive left a corrupt pending record behind",
            pendingStoreBytes.containsSequence(marker.encodeToByteArray()),
        )
        assertNull(pendingStore.getActive())
    }

    private suspend fun pendingStore_replacesIdentityAndGenerationCheckedCleanupDoesNotDeleteNewAttempt() {
        val store = pendingStore
        val oldAttempt = pendingAttempt(
            attemptId = "same-attempt",
            email = email("old@example.com"),
            dispatchGeneration = 1L,
        )
        val newAttempt = pendingAttempt(
            attemptId = "same-attempt",
            email = email("new@example.com"),
            dispatchGeneration = 2L,
        )

        store.replace(oldAttempt)
        store.replace(newAttempt)
        store.clear(oldAttempt.attemptId, oldAttempt.dispatchGeneration)

        assertEquals(newAttempt, pendingStore.getActive())

        store.clear(newAttempt.attemptId, newAttempt.dispatchGeneration)
        assertNull(pendingStore.getActive())
    }

    private suspend fun writeRawPreferences(
        name: String,
        update: (MutablePreferences) -> Unit,
    ) {
        val job = SupervisorJob()
        val store: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(job + Dispatchers.IO),
                produceFile = { context.preferencesDataStoreFile(name) },
            )
        try {
            store.edit(update)
        } finally {
            job.cancelAndJoin()
        }
    }

    private fun pendingAttempt(
        attemptId: String,
        email: EmailAddress,
        dispatchGeneration: Long = 0L,
    ) = PendingEmailOtpAttempt(
        attemptId = attemptId,
        email = email,
        resendAvailableAtEpochMillis = 1_000L,
        expiresAtEpochMillis = 60_000L,
        challengeMayBeActive = true,
        dispatchOutcome = DispatchOutcome.Confirmed,
        dispatchGeneration = dispatchGeneration,
    )

    private fun email(value: String): EmailAddress =
        when (val result = EmailAddressParser().parse(value)) {
            is AuthOutcome.Success -> result.value
            is AuthOutcome.Failure -> error("Invalid test email: $value")
        }

    private class IsolatedStorageContext(
        base: Context,
        testId: String,
    ) : ContextWrapper(base) {
        private val safeId = testId.replace(Regex("[^a-z0-9_]"), "_")
        private val root = File(base.cacheDir, "mee3-27-auth-persistence/$safeId")
        private val preferencesPrefix = "mee3_27_${safeId}_"

        override fun getApplicationContext(): Context = this

        override fun getFilesDir(): File = File(root, "files").apply(File::mkdirs)

        override fun getNoBackupFilesDir(): File = File(root, "no_backup").apply(File::mkdirs)

        override fun getSharedPreferences(
            name: String,
            mode: Int,
        ): SharedPreferences = super.getSharedPreferences(preferencesPrefix + name, mode)

        fun reset() {
            root.deleteRecursively()
            filesDir.mkdirs()
            clearIsolatedSharedPreferences()
        }

        fun clearIsolatedSharedPreferences() {
            listOf("meeting_token_keyset_prefs").forEach { name ->
                super
                    .getSharedPreferences(preferencesPrefix + name, MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
            }
        }

        fun dataStoreFile(name: String): File = preferencesDataStoreFile(name)

        fun persistedBytes(): ByteArray {
            val files =
                buildList {
                    root.walkTopDown().filter(File::isFile).forEach(::add)
                    val sharedPreferencesDir = File(applicationInfo.dataDir, "shared_prefs")
                    sharedPreferencesDir
                        .listFiles()
                        .orEmpty()
                        .filter { it.name.startsWith(preferencesPrefix) }
                        .forEach(::add)
                }
            return files.fold(ByteArray(0)) { bytes, file -> bytes + file.readBytes() }
        }
    }

    private companion object {
        const val TOKEN_STORE = "secure_token_store"
        const val PENDING_STORE = "pending_email_otp_store"
    }
}

private fun ByteArray.containsSequence(expected: ByteArray): Boolean {
    if (expected.isEmpty()) return true
    return indices.any { start ->
        start + expected.size <= size &&
            expected.indices.all { offset -> this[start + offset] == expected[offset] }
    }
}
