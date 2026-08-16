package dev.whysoezzy.meet.push

import com.google.firebase.messaging.RemoteMessage
import com.whysoezzy.auth.domain.models.AuthCredentialState
import com.whysoezzy.auth.domain.models.AuthSession
import com.whysoezzy.auth.domain.models.CredentialVersion
import com.whysoezzy.auth.domain.repository.AuthSessionRepository
import com.whysoezzy.domain.models.PushInstallation
import com.whysoezzy.domain.models.PushInstallationDeleteResult
import com.whysoezzy.domain.models.PushInstallationFid
import com.whysoezzy.domain.models.PushInstallationId
import com.whysoezzy.domain.models.PushInstallationStatus
import com.whysoezzy.domain.models.PushInstallationUpsertResult
import com.whysoezzy.domain.repository.PushInstallationRepository
import com.whysoezzy.network.error.ApiErrorMetadata
import com.whysoezzy.network.error.ApiException
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PushRegistrationCoordinatorTest {
    @Test
    fun `service path rejects A message captured before exit when B is current at handoff`() =
        runTest {
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
            val coordinator = PushRegistrationCoordinator(
                authSessionRepository = auth,
                installationRepository = RecordingInstallations(),
                fcm = RecordingFirebase(),
                stateStore = store,
                workScheduler = RecordingScheduler(),
            )
            val handoff = object : PushMessageHandoff {
                override fun captureExitEpoch(): Long {
                    val epoch = coordinator.captureExitEpoch()
                    runBlocking {
                        coordinator.beginAccountExit()
                        coordinator.endAccountExit()
                    }
                    auth.setSession(AuthSession(8L, AuthSession.Stage.Ready))
                    return epoch
                }

                override suspend fun handleDataMessage(
                    data: Map<String, String>,
                    hasNotificationBlock: Boolean,
                    ingressExitEpoch: Long,
                ) {
                    coordinator.handleDataMessage(
                        data = data,
                        hasNotificationBlock = hasNotificationBlock,
                        ingressExitEpoch = ingressExitEpoch,
                    )
                }
            }

            val message = mockk<RemoteMessage>()
            every { message.data } returns reminderData()
            every { message.notification } returns null
            MeetFirebaseMessagingService(handoff).onMessageReceived(message)

            assertTrue(store.state.ledger.isEmpty())
        }

    @Test
    fun `delayed data message from a departed epoch cannot bind to a replacement account`() =
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
            val coordinator = PushRegistrationCoordinator(
                authSessionRepository = auth,
                installationRepository = RecordingInstallations(),
                fcm = RecordingFirebase(),
                stateStore = store,
                workScheduler = RecordingScheduler(),
                dispatcher = dispatcher,
            )
            coordinator.start()
            runCurrent()

            coordinator.onDataMessage(
                data = reminderData(),
                hasNotificationBlock = false,
            )
            coordinator.beginAccountExit()
            coordinator.endAccountExit()
            auth.setSession(AuthSession(8L, AuthSession.Stage.Ready))
            runCurrent()

            assertTrue(store.state.ledger.isEmpty())
            coordinator.close()
        }

    @Test
    fun `tap consumption is rejected while account exit fence is active`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val owner = OwnerSnapshot(userId = 7L, generation = 1L)
            val eventId = "550e8400-e29b-41d4-a716-446655440000"
            val store = RecordingStateStore(
                PushStateV1(
                    registration = RegistrationState(
                        owner = owner,
                        accountGeneration = owner.generation,
                    ),
                    ledger = listOf(
                        LedgerRecord.OwnedReminderEvent(
                            eventId = eventId,
                            owner = owner,
                            meetingId = 42L,
                            reminderOffsetMinutes = 60,
                            issuedAt = 1L,
                            receivedAt = 2L,
                            status = OwnedEventStatus.DISPLAYED,
                            statusChangedAt = 2L,
                        ),
                    ),
                ),
            )
            val coordinator = PushRegistrationCoordinator(
                authSessionRepository = RecordingAuth(
                    AuthSession(7L, AuthSession.Stage.Ready),
                ),
                installationRepository = RecordingInstallations(),
                fcm = RecordingFirebase(),
                stateStore = store,
                workScheduler = RecordingScheduler(),
                dispatcher = dispatcher,
            )
            coordinator.beginAccountExit()

            var navigated = false
            val consumed = coordinator.consumeTap(
                command = PushTapCommand(eventId, 42L),
                isAlreadyAtDestination = { false },
                navigate = { navigated = true },
            )

            assertTrue(!consumed)
            assertTrue(!navigated)
            coordinator.endAccountExit()
        }

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
                deleteResults = listOf(
                    Result.failure(IllegalStateException("offline")),
                    Result.success(PushInstallationDeleteResult.Acknowledged),
                ),
            )
            val scheduler = RecordingScheduler()
            val firebase = RecordingFirebase()
            val coordinator = PushRegistrationCoordinator(
                authSessionRepository = auth,
                installationRepository = installations,
                fcm = firebase,
                stateStore = store,
                workScheduler = scheduler,
                dispatcher = dispatcher,
            )
            coordinator.start()
            runCurrent()
            scheduler.enqueues = 0

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
            assertEquals(1, scheduler.enqueues)

            coordinator.reconcileCurrent()
            coordinator.onRegistered("fid-b")
            runCurrent()
            coordinator.reconcileCurrent()

            assertEquals(
                PushInstallationId("550e8400-e29b-41d4-a716-446655440001"),
                installations.createdInstallationId,
            )
            assertEquals(1, firebase.registers)
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
                deleteResults = listOf(
                    Result.success(
                        PushInstallationDeleteResult.Terminal(
                            com.whysoezzy.domain.models.PushInstallationTerminalStatus
                                .MALFORMED_SUCCESS,
                        ),
                    ),
                ),
            )
            val auth = RecordingAuth(AuthSession(7L, AuthSession.Stage.Ready))
            val scheduler = RecordingScheduler()
            val firebase = RecordingFirebase()
            val coordinator = PushRegistrationCoordinator(
                authSessionRepository = auth,
                installationRepository = installations,
                fcm = firebase,
                stateStore = store,
                workScheduler = scheduler,
                dispatcher = dispatcher,
            )
            coordinator.start()
            runCurrent()
            scheduler.enqueues = 0

            auth.setSession(AuthSession(8L, AuthSession.Stage.Ready))
            runCurrent()

            assertEquals(1, store.state.accountCleanup?.retryAttempt)
            assertEquals(
                RegistrationTerminal.MALFORMED_SUCCESS,
                store.state.accountCleanup?.terminal,
            )
            assertTrue(scheduler.enqueues > 0)

            coordinator.reconcileCurrent()
            coordinator.onRegistered("fid-b")
            runCurrent()
            coordinator.reconcileCurrent()

            assertEquals(
                PushInstallationId("550e8400-e29b-41d4-a716-446655440001"),
                installations.createdInstallationId,
            )
            assertEquals(listOf(oldInstallationId), installations.deleted)
            assertEquals(1, firebase.registers)
            coordinator.close()
        }

    @Test
    fun `account replacement terminal HTTP failures rearm current account registration`() =
        runTest {
            listOf(403, 409).forEach { status ->
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
                    deleteResults = listOf(
                        Result.failure(
                            when (status) {
                                403 -> ApiException.UnauthorizedError(
                                    ApiErrorMetadata(status = status, code = "TERMINAL"),
                                )
                                else -> ApiException.ServerError(
                                    ApiErrorMetadata(status = status, code = "TERMINAL"),
                                )
                            },
                        ),
                    ),
                )
                val scheduler = RecordingScheduler()
                val firebase = RecordingFirebase()
                val coordinator = PushRegistrationCoordinator(
                    authSessionRepository = auth,
                    installationRepository = installations,
                    fcm = firebase,
                    stateStore = store,
                    workScheduler = scheduler,
                    dispatcher = dispatcher,
                )
                coordinator.start()
                runCurrent()
                scheduler.enqueues = 0

                auth.setSession(AuthSession(8L, AuthSession.Stage.Ready))
                runCurrent()

                assertEquals(
                    when (status) {
                        403 -> RegistrationTerminal.FORBIDDEN
                        else -> RegistrationTerminal.CONFLICT_BLOCKED
                    },
                    store.state.accountCleanup?.terminal,
                )
                assertTrue(scheduler.enqueues > 0)

                coordinator.reconcileCurrent()
                coordinator.onRegistered("fid-b")
                runCurrent()
                coordinator.reconcileCurrent()

                assertEquals(
                    PushInstallationId("550e8400-e29b-41d4-a716-446655440001"),
                    installations.createdInstallationId,
                )
                assertEquals(listOf(oldInstallationId), installations.deleted)
                assertEquals(1, firebase.registers)
                coordinator.close()
            }
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
        deleteResults: List<Result<PushInstallationDeleteResult>> = listOf(
            Result.success(PushInstallationDeleteResult.Acknowledged),
        ),
    ) : PushInstallationRepository {
        private val remainingDeleteResults = deleteResults.toMutableList()
        val deleted = mutableListOf<String>()
        var createdInstallationId: PushInstallationId? = null

        override suspend fun create(
            fid: PushInstallationFid,
        ): Result<PushInstallationUpsertResult> {
            createdInstallationId = PushInstallationId(
                "550e8400-e29b-41d4-a716-446655440001",
            )
            return Result.success(
                PushInstallationUpsertResult.Acknowledged(
                    PushInstallation(
                        installationId = createdInstallationId!!,
                        status = PushInstallationStatus.ACTIVE,
                        lastSeenAt = Instant.EPOCH,
                    ),
                ),
            )
        }

        override suspend fun update(
            installationId: PushInstallationId,
            fid: PushInstallationFid,
        ): Result<PushInstallationUpsertResult> =
            error("Unexpected update")

        override suspend fun delete(
            installationId: PushInstallationId,
        ): Result<PushInstallationDeleteResult> {
            deleted += installationId.value
            return if (remainingDeleteResults.isNotEmpty()) {
                remainingDeleteResults.removeAt(0)
            } else {
                Result.success(PushInstallationDeleteResult.Acknowledged)
            }
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

    private companion object {
        fun reminderData(): Map<String, String> = mapOf(
            "eventType" to "MEETING_REMINDER",
            "schemaVersion" to "1",
            "eventId" to "550e8400-e29b-41d4-a716-446655440000",
            "meetingId" to "42",
            "reminderOffsetMinutes" to "60",
            "issuedAt" to "2026-01-01T00:00:00Z",
            "destination" to "MEETING_DETAILS",
        )
    }
}
