package dev.whysoezzy.meet.push

import com.whysoezzy.auth.domain.models.AuthCredentialState
import com.whysoezzy.auth.domain.models.AuthSession
import com.whysoezzy.auth.domain.models.CredentialVersion
import com.whysoezzy.auth.domain.repository.AuthSessionRepository
import com.whysoezzy.domain.models.PushInstallationDeleteResult
import com.whysoezzy.domain.models.PushInstallationFid
import com.whysoezzy.domain.models.PushInstallationId
import com.whysoezzy.domain.models.PushInstallationUpsertResult
import com.whysoezzy.domain.repository.PushInstallationRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PushRegistrationCoordinatorTest {
    @Test
    fun `unregistered and registered callbacks cannot resurrect state during account exit`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val owner = OwnerSnapshot(userId = 7L, generation = 1L)
            val store = RecordingStateStore(
                PushStateV1(
                    registration = RegistrationState(
                        owner = owner,
                        accountGeneration = owner.generation,
                    ),
                ),
            )
            val auth = RecordingAuth(AuthSession(7L, AuthSession.Stage.Ready))
            val scheduler = RecordingScheduler()
            val registerGate = CompletableDeferred<Unit>()
            val firebase = RecordingFirebase(registerGate)
            val coordinator = PushRegistrationCoordinator(
                authSessionRepository = auth,
                installationRepository = RecordingInstallations(),
                fcm = firebase,
                stateStore = store,
                workScheduler = scheduler,
                dispatcher = dispatcher,
            )
            coordinator.start()
            runCurrent()
            scheduler.enqueues = 0

            val reconciliation = async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.reconcileCurrent()
            }
            coordinator.onUnregistered()
            coordinator.onRegistered("fid-b")
            coordinator.beginAccountExit()
            runCurrent()

            assertEquals(0, scheduler.enqueues)
            assertEquals(1, firebase.registers)
            assertEquals(null, store.state.registration.pendingFid)

            coordinator.endAccountExit()
            registerGate.complete(Unit)
            assertTrue(reconciliation.await())
            runCurrent()
            assertEquals(null, store.state.registration.pendingFid)
            assertEquals(0, scheduler.enqueues)
            coordinator.close()
        }

    @Test
    fun `authenticated account replacement deletes old installation before clearing owner`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val oldOwner = OwnerSnapshot(userId = 7L, generation = 1L)
            val oldInstallationId = "550e8400-e29b-41d4-a716-446655440000"
            val store = RecordingStateStore(
                PushStateV1(
                    registration = RegistrationState(
                        owner = oldOwner,
                        accountGeneration = oldOwner.generation,
                        installationId = oldInstallationId,
                    ),
                ),
            )
            val auth = RecordingAuth(AuthSession(7L, AuthSession.Stage.Ready))
            val installations = RecordingInstallations()
            val coordinator = PushRegistrationCoordinator(
                authSessionRepository = auth,
                installationRepository = installations,
                fcm = RecordingFirebase(),
                stateStore = store,
                workScheduler = RecordingScheduler(),
                dispatcher = dispatcher,
            )
            coordinator.start()
            runCurrent()

            auth.setSession(AuthSession(8L, AuthSession.Stage.Ready))
            runCurrent()

            assertEquals(listOf(oldInstallationId), installations.deleted)
            assertEquals(null, store.state.registration.owner)
            coordinator.close()
        }

    @Test
    fun `account replacement preserves cleanup retry after delete failure`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val oldOwner = OwnerSnapshot(userId = 7L, generation = 1L)
            val oldInstallationId = "550e8400-e29b-41d4-a716-446655440000"
            val store = RecordingStateStore(
                PushStateV1(
                    registration = RegistrationState(
                        owner = oldOwner,
                        accountGeneration = oldOwner.generation,
                        installationId = oldInstallationId,
                    ),
                ),
            )
            val auth = RecordingAuth(AuthSession(7L, AuthSession.Stage.Ready))
            val installations = RecordingInstallations(
                deleteResult = Result.failure(IllegalStateException("offline")),
            )
            val coordinator = PushRegistrationCoordinator(
                authSessionRepository = auth,
                installationRepository = installations,
                fcm = RecordingFirebase(),
                stateStore = store,
                workScheduler = RecordingScheduler(),
                dispatcher = dispatcher,
            )
            coordinator.start()
            runCurrent()

            auth.setSession(AuthSession(8L, AuthSession.Stage.Ready))
            runCurrent()

            assertEquals(listOf(oldInstallationId), installations.deleted)
            assertEquals(null, store.state.registration.owner)
            assertNotNull(store.state.accountCleanup)
            assertEquals(oldOwner, store.state.accountCleanup?.owner)
            assertEquals(oldInstallationId, store.state.accountCleanup?.installationId)
            assertEquals(1, store.state.accountCleanup?.retryAttempt)
            assertEquals(
                RegistrationTerminal.NONE,
                store.state.accountCleanup?.terminal,
            )
            coordinator.close()
        }

    @Test
    fun `account replacement preserves terminal cleanup outcome`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val oldOwner = OwnerSnapshot(userId = 7L, generation = 1L)
            val oldInstallationId = "550e8400-e29b-41d4-a716-446655440000"
            val store = RecordingStateStore(
                PushStateV1(
                    registration = RegistrationState(
                        owner = oldOwner,
                        accountGeneration = oldOwner.generation,
                        installationId = oldInstallationId,
                    ),
                ),
            )
            val installations = RecordingInstallations(
                deleteResult = Result.success(
                    PushInstallationDeleteResult.Terminal(
                        com.whysoezzy.domain.models.PushInstallationTerminalStatus
                            .MALFORMED_SUCCESS,
                    ),
                ),
            )
            val auth = RecordingAuth(AuthSession(7L, AuthSession.Stage.Ready))
            val coordinator = PushRegistrationCoordinator(
                authSessionRepository = auth,
                installationRepository = installations,
                fcm = RecordingFirebase(),
                stateStore = store,
                workScheduler = RecordingScheduler(),
                dispatcher = dispatcher,
            )
            coordinator.start()
            runCurrent()

            auth.setSession(AuthSession(8L, AuthSession.Stage.Ready))
            runCurrent()

            assertEquals(1, store.state.accountCleanup?.retryAttempt)
            assertEquals(
                RegistrationTerminal.MALFORMED_SUCCESS,
                store.state.accountCleanup?.terminal,
            )
            coordinator.close()
        }

    private class RecordingStateStore(
        var state: PushStateV1,
    ) : PushStateStore {
        override suspend fun read(): PushStateV1 = state

        override suspend fun update(transform: (PushStateV1) -> PushStateV1): PushStateV1 {
            state = transform(state)
            return state
        }
    }

    private class RecordingScheduler : PushWorkScheduler {
        var enqueues = 0
        var cancels = 0

        override fun enqueue() {
            enqueues++
        }

        override fun cancel() {
            cancels++
        }
    }

    private class RecordingFirebase(
        private val registerGate: CompletableDeferred<Unit>? = null,
    ) : FcmRegistrationClient {
        var registers = 0

        override suspend fun register() {
            registers++
            registerGate?.await()
        }

        override fun unregister() = Unit
    }

    private class RecordingInstallations(
        private val deleteResult: Result<PushInstallationDeleteResult> =
            Result.success(PushInstallationDeleteResult.Acknowledged),
    ) : PushInstallationRepository {
        val deleted = mutableListOf<String>()

        override suspend fun create(
            fid: PushInstallationFid,
        ): Result<PushInstallationUpsertResult> =
            error("Unexpected create")

        override suspend fun update(
            installationId: PushInstallationId,
            fid: PushInstallationFid,
        ): Result<PushInstallationUpsertResult> =
            error("Unexpected update")

        override suspend fun delete(
            installationId: PushInstallationId,
        ): Result<PushInstallationDeleteResult> {
            deleted += installationId.value
            return deleteResult
        }
    }

    private class RecordingAuth(
        initial: AuthSession,
    ) : AuthSessionRepository {
        private val state = MutableStateFlow(
            AuthCredentialState(initial, CredentialVersion("epoch", 0L)),
        )

        override val session: StateFlow<AuthSession> =
            MutableStateFlow(initial).asStateFlow()
        override val credentialState: StateFlow<AuthCredentialState> = state.asStateFlow()

        override suspend fun read(): AuthSession = state.value.session

        fun setSession(next: AuthSession) {
            state.value = AuthCredentialState(next, state.value.credentialVersion)
        }

        override suspend fun saveAuthenticated(
            userId: Long,
            stage: AuthSession.Stage,
        ) = Unit

        override suspend fun compareAndSetStage(
            expected: AuthSession.Stage,
            next: AuthSession.Stage,
        ): Boolean = false

        override suspend fun clear() {
            state.value = AuthCredentialState(AuthSession.LoggedOut, state.value.credentialVersion)
        }
    }
}
