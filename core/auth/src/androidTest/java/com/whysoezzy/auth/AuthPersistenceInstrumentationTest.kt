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

    @Before
    fun setUp() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        context = IsolatedStorageContext(targetContext, "real_store_contract")
        context.reset()
    }

    @After
    fun tearDown() = runBlocking {
        // Clear through the real APIs so no authentication material survives this test run.
        runCatching { DataStoreTokenManager(context).clearTokens() }
        runCatching { DataStorePendingEmailOtpStore(context).clearActive() }
        context.clearIsolatedSharedPreferences()
    }

    @Test
    fun realStores_verifyPersistenceContractWithoutTouchingInstalledAppData() = runBlocking {
        corruptEncryptedState_failsClosed_andPendingRecordIsRemoved()
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

        DataStoreTokenManager(context).saveAuthenticated(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = userId,
            stage = AuthSession.Stage.NeedsName,
        )
        DataStorePendingEmailOtpStore(context).replace(attempt)

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

        val recreatedTokens = DataStoreTokenManager(context)
        val recreatedSession = DataStoreAuthSessionRepository(recreatedTokens)
        val recreatedPending = DataStorePendingEmailOtpStore(context)
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

        assertNull(DataStoreTokenManager(context).loadTokens())
        val clearedTokens = DataStoreTokenManager(context)
        assertEquals(AuthSession.LoggedOut, DataStoreAuthSessionRepository(clearedTokens).read())
        assertNull(DataStorePendingEmailOtpStore(context).getActive())
    }

    private suspend fun legacyTokenOnlyState_canBeMigratedToReadyAndSurvivesRecreation() {
        val legacyTokens = DataStoreTokenManager(context)
        legacyTokens.saveTokens("legacy-access", "legacy-refresh", 42L)

        // Reading a valid install that predates the stage key promotes it to Ready and
        // persists the migration into the same encrypted token transaction.
        assertEquals(
            AuthSession(42L, AuthSession.Stage.Ready),
            DataStoreAuthSessionRepository(legacyTokens).read(),
        )

        val recreatedTokens = DataStoreTokenManager(context)
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

    private suspend fun authSession_allowsForwardCasAndIdentityReplacement_butRejectsStaleTransitions() {
        val tokenManager = DataStoreTokenManager(context)
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
            DataStoreAuthSessionRepository(DataStoreTokenManager(context)).read(),
        )
        assertEquals("replacement-access", DataStoreTokenManager(context).getAccessToken())
    }

    private suspend fun corruptEncryptedState_failsClosed_andPendingRecordIsRemoved() {
        val marker = "not-valid-tink-ciphertext"
        writeRawPreferences(TOKEN_STORE) {
            it[stringPreferencesKey("access_token")] = marker
            it[stringPreferencesKey("refresh_token")] = marker
            it[stringPreferencesKey("user_id")] = marker
            it[stringPreferencesKey("stage")] = marker
        }
        writeRawPreferences(PENDING_STORE) {
            it[stringPreferencesKey("pending_email_otp_record")] = marker
        }

        val tokens = DataStoreTokenManager(context)
        assertFalse(tokens.isLoggedInFlow.first())
        assertEquals(AuthSession.LoggedOut, DataStoreAuthSessionRepository(tokens).read())
        assertNull(tokens.loadTokens())
        assertNull(tokens.getAccessToken())
        assertNull(tokens.getRefreshToken())
        assertNull(tokens.getUserId())
        assertFalse(
            "Corrupt token/session record was not cleaned up",
            context
                .dataStoreFile(TOKEN_STORE)
                .readBytes()
                .containsSequence(marker.encodeToByteArray()),
        )

        val pending = DataStorePendingEmailOtpStore(context)
        assertNull(pending.getActive())
        assertFalse(
            "Corrupt pending record was not cleaned up",
            context
                .dataStoreFile(PENDING_STORE)
                .readBytes()
                .containsSequence(marker.encodeToByteArray()),
        )
    }

    private suspend fun pendingStore_replacesIdentityAndGenerationCheckedCleanupDoesNotDeleteNewAttempt() {
        val store = DataStorePendingEmailOtpStore(context)
        val oldAttempt = pendingAttempt("old-attempt", email("old@example.com"))
        val newAttempt = pendingAttempt("new-attempt", email("new@example.com"))

        store.replace(oldAttempt)
        store.replace(newAttempt)
        store.clear(oldAttempt.attemptId)

        assertNull(store.get(oldAttempt.attemptId))
        assertEquals(newAttempt, DataStorePendingEmailOtpStore(context).getActive())

        store.clear(newAttempt.attemptId)
        assertNull(DataStorePendingEmailOtpStore(context).getActive())
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
    ) = PendingEmailOtpAttempt(
        attemptId = attemptId,
        email = email,
        resendAvailableAtEpochMillis = 1_000L,
        expiresAtEpochMillis = 60_000L,
        challengeMayBeActive = true,
        dispatchOutcome = DispatchOutcome.Confirmed,
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
