package dev.whysoezzy.meet.push

import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
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
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

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
    fun `atomic aged-record eviction cancels displayed notifications after commit`() =
        runTest {
            val owner = OwnerSnapshot(userId = 7L, generation = 1L)
            val now = PUSH_LEDGER_RETENTION_MILLIS + 100L
            val store = RecordingStateStore(
                PushStateV1(
                    registration = RegistrationState(
                        owner = owner,
                        accountGeneration = owner.generation,
                    ),
                    ledger = buildList {
                        add(
                            LedgerRecord.OwnedReminderEvent(
                                eventId = "0-displayed",
                                owner = owner,
                                meetingId = 1L,
                                reminderOffsetMinutes = 60,
                                issuedAt = 1L,
                                receivedAt = 1L,
                                status = OwnedEventStatus.DISPLAYED,
                                statusChangedAt = 0L,
                            ),
                        )
                        repeat(PUSH_LEDGER_CAPACITY - 1) { index ->
                            add(
                                LedgerRecord.DedupeTombstone(
                                    eventId = "tombstone-$index",
                                    reason = TombstoneReason.DISCARDED_NO_OWNER,
                                    terminalAt = 0L,
                                ),
                            )
                        }
                    },
                ),
            )
            val presentation = RecordingPresentation {
                store.state.ledger.none { it.eventId == "0-displayed" }
            }
            val coordinator = PushRegistrationCoordinator(
                authSessionRepository = RecordingAuth(
                    AuthSession(owner.userId, AuthSession.Stage.Ready),
                ),
                installationRepository = RecordingInstallations(),
                fcm = RecordingFirebase(),
                stateStore = store,
                presentation = presentation,
                workScheduler = RecordingScheduler(),
            )

            coordinator.handleDataMessage(
                data = reminderData(),
                hasNotificationBlock = false,
                ingressExitEpoch = coordinator.captureExitEpoch(),
            )

            assertEquals(listOf("0-displayed"), presentation.cancelled)
            assertTrue(presentation.committedAtCancellation)
            assertTrue(store.state.ledger.none { it.eventId == "0-displayed" })
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
            assertEquals(0, firebase.registers)
            assertEquals(null, store.state.registration.pendingFid)

            coordinator.endAccountExit()
            registerGate.complete(Unit)
            reconciliation.join()
            runCurrent()
            assertTrue(reconciliation.isCancelled)
            assertEquals(null, store.state.registration.pendingFid)
            assertEquals(0, scheduler.enqueues)
            coordinator.close()
        }

    @Test
    fun `activation follow-up reaches OPEN only after Firebase callback and backend acknowledgement`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val auth = RecordingAuth(AuthSession(7L, AuthSession.Stage.Ready))
            val backendGate = CompletableDeferred<Result<PushInstallationUpsertResult>>()
            val installations = RecordingInstallations(createGates = listOf(backendGate))
            val firebase = RecordingFirebase()
            val scheduler = RecordingScheduler()
            val coordinator = PushRegistrationCoordinator(
                authSessionRepository = auth,
                installationRepository = installations,
                fcm = firebase,
                stateStore = RecordingStateStore(PushStateV1()),
                workScheduler = scheduler,
                dispatcher = dispatcher,
            )

            val lease = coordinator.beginAccountExitLease()
            coordinator.endAccountExit(lease)
            runCurrent()
            coordinator.onUnregistered()
            runCurrent()

            assertEquals(PushLifecyclePhase.ACTIVATING_CURRENT, coordinator.lifecyclePhase)
            assertEquals(1, scheduler.enqueues)

            val firebaseFollowUp = async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.reconcileCurrent()
            }
            runCurrent()
            assertEquals(1, firebase.registers)
            coordinator.onRegistered("fid-b")
            runCurrent()
            firebaseFollowUp.await()
            runCurrent()

            assertEquals(2, scheduler.enqueues)
            assertEquals(PushLifecyclePhase.ACTIVATING_CURRENT, coordinator.lifecyclePhase)

            val backendActivation = async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.reconcileCurrent()
            }
            runCurrent()
            assertEquals(1, installations.createdFids.size)
            assertEquals(PushLifecyclePhase.ACTIVATING_CURRENT, coordinator.lifecyclePhase)
            backendGate.complete(Result.success(acknowledgedInstallationResult()))
            runCurrent()
            backendActivation.await()

            assertEquals(
                PushInstallationId("550e8400-e29b-41d4-a716-446655440001"),
                installations.createdInstallationId,
            )
            assertEquals(PushLifecyclePhase.OPEN, coordinator.lifecyclePhase)
            coordinator.close()
        }

    @Test
    fun `same-owner stale credential-version completions are no-ops and current version converges`() =
        runTest {
            val outcomes = listOf(
                "success" to Result.success<PushInstallationUpsertResult>(
                    acknowledgedInstallationResult(),
                ),
                "unauthorized" to Result.failure<PushInstallationUpsertResult>(
                    ApiException.UnauthorizedError(
                        ApiErrorMetadata(status = 401, code = "STALE"),
                    ),
                ),
                "transient" to Result.failure<PushInstallationUpsertResult>(
                    ApiException.ServerError(
                        ApiErrorMetadata(status = 500, code = "STALE"),
                    ),
                ),
                "terminal" to Result.failure<PushInstallationUpsertResult>(
                    ApiException.ServerError(
                        ApiErrorMetadata(status = 403, code = "STALE"),
                    ),
                ),
            )
            outcomes.forEach { (_, staleResult) ->
                val dispatcher = StandardTestDispatcher(testScheduler)
                val owner = OwnerSnapshot(userId = 7L, generation = 1L)
                val staleCompletion = CompletableDeferred<Result<PushInstallationUpsertResult>>()
                val auth = RecordingAuth(
                    initial = AuthSession(owner.userId, AuthSession.Stage.Ready),
                    initialCredentialVersion = CredentialVersion("epoch", 1L),
                )
                val installations = RecordingInstallations(
                    createGates = listOf(staleCompletion),
                )
                val scheduler = RecordingScheduler()
                val store = RecordingStateStore(
                    PushStateV1(
                        registration = RegistrationState(
                            owner = owner,
                            accountGeneration = owner.generation,
                            pendingFid = "fid-a",
                        ),
                    ),
                )
                val coordinator = PushRegistrationCoordinator(
                    authSessionRepository = auth,
                    installationRepository = installations,
                    fcm = RecordingFirebase(),
                    stateStore = store,
                    workScheduler = scheduler,
                    dispatcher = dispatcher,
                )

                val oldRequest = async(start = CoroutineStart.UNDISPATCHED) {
                    coordinator.reconcileCurrent()
                }
                runCurrent()
                auth.setCredentialVersion(CredentialVersion("epoch", 2L))
                staleCompletion.complete(staleResult)
                runCurrent()
                oldRequest.await()

                assertEquals(null, store.state.registration.installationId)
                assertEquals(RegistrationTerminal.NONE, store.state.registration.terminal)
                assertEquals(0, store.state.registration.retryAttempt)
                assertEquals(0, scheduler.enqueues)

                coordinator.reconcileCurrent()

                assertEquals(
                    PushInstallationId("550e8400-e29b-41d4-a716-446655440001"),
                    installations.createdInstallationId,
                )
                assertEquals(RegistrationTerminal.NONE, store.state.registration.terminal)
                coordinator.close()
            }
        }

    @Test
    fun `admitted child cancellation is observed by drain before reopening ingress`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val updateGate = CompletableDeferred<Unit>()
            val unregisterGate = CompletableDeferred<Result<Unit>>()
            val owner = OwnerSnapshot(userId = 7L, generation = 1L)
            val store = RecordingStateStore(
                PushStateV1(
                    registration = RegistrationState(
                        owner = owner,
                        accountGeneration = owner.generation,
                    ),
                ),
                updateGate = updateGate,
            )
            val auth = RecordingAuth(AuthSession(owner.userId, AuthSession.Stage.Ready))
            val coordinator = PushRegistrationCoordinator(
                authSessionRepository = auth,
                installationRepository = RecordingInstallations(),
                fcm = RecordingFirebase(unregisterGate = unregisterGate),
                stateStore = store,
                workScheduler = RecordingScheduler(),
                dispatcher = dispatcher,
            )

            var completed = false
            coordinator.onDataMessage(
                data = reminderData(),
                hasNotificationBlock = false,
                onComplete = { completed = true },
            )
            runCurrent()

            coordinator.beginAccountExit()
            assertEquals(PushLifecyclePhase.EXITING, coordinator.lifecyclePhase)
            coordinator.endAccountExit()
            assertEquals(PushLifecyclePhase.DRAINING, coordinator.lifecyclePhase)
            assertFalse(completed)

            updateGate.complete(Unit)
            runCurrent()
            unregisterGate.complete(Result.success(Unit))
            runCurrent()
            advanceUntilIdle()
            coordinator.onUnregistered()
            runCurrent()
            advanceUntilIdle()

            assertTrue(completed)
            assertEquals(PushLifecyclePhase.ACTIVATING_CURRENT, coordinator.lifecyclePhase)
            assertTrue(store.state.ledger.isEmpty())
            coordinator.close()
        }

    @Test
    fun `drain read failure retries and recovers without stranding logged-out lifecycle`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val store = RecordingStateStore(
                PushStateV1(),
                readFailures = 1,
            )
            val coordinator = loggedOutCoordinator(dispatcher, store)

            releaseDrain(coordinator)
            advanceUntilIdle()

            assertEquals(2, store.readAttempts)
            assertEquals(1_000L, testScheduler.currentTime)
            assertEquals(PushLifecyclePhase.OPEN, coordinator.lifecyclePhase)
            coordinator.close()
        }

    @Test
    fun `drain read failure exhaustion enters DRAIN_BLOCKED`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val store = RecordingStateStore(
                PushStateV1(),
                readFailures = 6,
            )
            val coordinator = loggedOutCoordinator(dispatcher, store)

            releaseDrain(coordinator)
            advanceUntilIdle()

            assertEquals(6, store.readAttempts)
            assertEquals(31_000L, testScheduler.currentTime)
            assertEquals(PushLifecyclePhase.DRAIN_BLOCKED, coordinator.lifecyclePhase)
            coordinator.close()
        }

    @Test
    fun `drain scrub failure retries and recovers`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val store = RecordingStateStore(PushStateV1(), updateFailures = 1)
            val coordinator = loggedOutCoordinator(dispatcher = dispatcher, store = store)

            releaseDrain(coordinator)
            advanceUntilIdle()

            assertEquals(2, store.updateAttempts)
            assertEquals(1_000L, testScheduler.currentTime)
            assertEquals(PushLifecyclePhase.OPEN, coordinator.lifecyclePhase)
            coordinator.close()
        }

    @Test
    fun `drain scrub failure exhaustion remains fail closed`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val store = RecordingStateStore(PushStateV1(), updateFailures = 6)
            val coordinator = loggedOutCoordinator(dispatcher = dispatcher, store = store)

            releaseDrain(coordinator)
            advanceUntilIdle()

            assertEquals(6, store.updateAttempts)
            assertEquals(31_000L, testScheduler.currentTime)
            assertEquals(PushLifecyclePhase.DRAIN_BLOCKED, coordinator.lifecyclePhase)
            coordinator.close()
        }

    @Test
    fun `successful notification cancellation is not retried or falsely blocks drain`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            var cancelCalls = 0
            val presentation = RecordingPresentation(
                onCancel = {
                    cancelCalls++
                    if (cancelCalls > 1) error("cancellation should not be retried")
                },
            )
            val owner = OwnerSnapshot(userId = 7L, generation = 1L)
            val store = RecordingStateStore(
                PushStateV1(
                    registration = RegistrationState(
                        owner = owner,
                        accountGeneration = owner.generation,
                        installationId = "550e8400-e29b-41d4-a716-446655440000",
                    ),
                    ledger = listOf(
                        LedgerRecord.OwnedReminderEvent(
                            eventId = "550e8400-e29b-41d4-a716-446655440000",
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
            val coordinator = loggedOutCoordinator(
                dispatcher = dispatcher,
                store = store,
                presentation = presentation,
            )

            releaseDrain(coordinator)
            advanceUntilIdle()

            assertEquals(1, cancelCalls)
            assertEquals(PushLifecyclePhase.OPEN, coordinator.lifecyclePhase)
            coordinator.close()
        }

    @Test
    fun `effect authorization is rechecked after lifecycle hook for scheduler and navigation`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            lateinit var schedulerCoordinator: PushRegistrationCoordinator
            val scheduler = RecordingScheduler()
            val owner = OwnerSnapshot(userId = 7L, generation = 1L)
            schedulerCoordinator = PushRegistrationCoordinator(
                authSessionRepository = RecordingAuth(AuthSession(owner.userId, AuthSession.Stage.Ready)),
                installationRepository = RecordingInstallations(),
                fcm = RecordingFirebase(),
                stateStore = RecordingStateStore(
                    PushStateV1(
                        registration = RegistrationState(
                            owner = owner,
                            accountGeneration = owner.generation,
                            installationId = "550e8400-e29b-41d4-a716-446655440000",
                            pendingFid = "fid-a",
                        ),
                    ),
                ),
                workScheduler = scheduler,
                dispatcher = dispatcher,
                beforeEffectInvocation = { kind ->
                    if (kind == LifecycleEffectKind.SCHEDULER_ENQUEUE) {
                        schedulerCoordinator.beginAccountExit()
                    }
                },
            )
            schedulerCoordinator.start()
            var schedulerRunWasCancelled = false
            try {
                runCurrent()
            } catch (_: CancellationException) {
                schedulerRunWasCancelled = true
            }

            assertEquals(0, scheduler.enqueues)
            assertEquals(PushLifecyclePhase.EXITING, schedulerCoordinator.lifecyclePhase)
            assertTrue(schedulerRunWasCancelled || schedulerCoordinator.lifecyclePhase == PushLifecyclePhase.EXITING)
            schedulerCoordinator.close()

            lateinit var navigationCoordinator: PushRegistrationCoordinator
            var navigated = false
            navigationCoordinator = PushRegistrationCoordinator(
                authSessionRepository = RecordingAuth(AuthSession(owner.userId, AuthSession.Stage.Ready)),
                installationRepository = RecordingInstallations(),
                fcm = RecordingFirebase(),
                stateStore = RecordingStateStore(
                    PushStateV1(
                        registration = RegistrationState(
                            owner = owner,
                            accountGeneration = owner.generation,
                        ),
                        ledger = listOf(
                            LedgerRecord.OwnedReminderEvent(
                                eventId = "550e8400-e29b-41d4-a716-446655440000",
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
                ),
                workScheduler = RecordingScheduler(),
                dispatcher = dispatcher,
                mainDispatcher = dispatcher,
                beforeEffectInvocation = { kind ->
                    if (kind == LifecycleEffectKind.NAVIGATION) {
                        navigationCoordinator.beginAccountExit()
                    }
                },
            )
            val tap = async(start = CoroutineStart.UNDISPATCHED) {
                navigationCoordinator.consumeTap(
                    command = PushTapCommand(
                        "550e8400-e29b-41d4-a716-446655440000",
                        42L,
                    ),
                    isAlreadyAtDestination = { false },
                    navigate = { navigated = true },
                )
            }
            var navigationRunWasCancelled = false
            try {
                runCurrent()
            } catch (_: CancellationException) {
                navigationRunWasCancelled = true
            }

            tap.join()
            assertFalse(navigated)
            assertTrue(navigationRunWasCancelled || tap.isCancelled)
            assertEquals(PushLifecyclePhase.EXITING, navigationCoordinator.lifecyclePhase)
            navigationCoordinator.close()
        }

    @Test
    fun `pending presentation effect is suppressed when exit begins before invocation`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val owner = OwnerSnapshot(userId = 7L, generation = 1L)
            lateinit var coordinator: PushRegistrationCoordinator
            val presentation = RecordingPresentation(
                onPresent = { error("presentation must be suppressed") },
            )
            coordinator = PushRegistrationCoordinator(
                authSessionRepository = RecordingAuth(AuthSession(owner.userId, AuthSession.Stage.Ready)),
                installationRepository = RecordingInstallations(),
                fcm = RecordingFirebase(),
                stateStore = RecordingStateStore(
                    PushStateV1(
                        registration = RegistrationState(
                            owner = owner,
                            accountGeneration = owner.generation,
                        ),
                    ),
                ),
                presentation = presentation,
                workScheduler = RecordingScheduler(),
                dispatcher = dispatcher,
                beforeEffectInvocation = { kind ->
                    if (kind == LifecycleEffectKind.PRESENT) {
                        coordinator.beginAccountExit()
                    }
                },
            )

            val message = async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.handleDataMessage(
                    data = reminderData(),
                    hasNotificationBlock = false,
                    ingressExitEpoch = coordinator.captureExitEpoch(),
                )
            }
            var cancelledByLifecycle = false
            try {
                runCurrent()
            } catch (_: CancellationException) {
                cancelledByLifecycle = true
            }
            message.join()

            assertTrue(cancelledByLifecycle || message.isCancelled)
            assertTrue(presentation.presented.isEmpty())
            assertEquals(PushLifecyclePhase.EXITING, coordinator.lifecyclePhase)
            coordinator.close()
        }

    @Test
    fun `scheduler presentation and navigation use their injected dispatcher affinities`() =
        runTest {
            val marker = ThreadLocal<String?>()
            val base = StandardTestDispatcher(testScheduler)
            val io = MarkerDispatcher(base, marker, "io")
            val main = MarkerDispatcher(base, marker, "main")
            val owner = OwnerSnapshot(userId = 7L, generation = 1L)

            val schedulerMarkers = mutableListOf<String?>()
            val scheduler = RecordingScheduler(
                onCancel = { schedulerMarkers += marker.get() },
            )
            val schedulerCoordinator = PushRegistrationCoordinator(
                authSessionRepository = RecordingAuth(AuthSession.LoggedOut),
                installationRepository = RecordingInstallations(),
                fcm = RecordingFirebase(),
                stateStore = RecordingStateStore(PushStateV1()),
                workScheduler = scheduler,
                dispatcher = io,
                mainDispatcher = main,
            )
            releaseDrain(schedulerCoordinator)
            advanceUntilIdle()
            assertEquals(listOf("io"), schedulerMarkers)
            schedulerCoordinator.close()

            val presentationMarkers = mutableListOf<String?>()
            val presentation = RecordingPresentation(
                onPresent = { presentationMarkers += marker.get() },
            )
            val presentationCoordinator = PushRegistrationCoordinator(
                authSessionRepository = RecordingAuth(AuthSession(owner.userId, AuthSession.Stage.Ready)),
                installationRepository = RecordingInstallations(),
                fcm = RecordingFirebase(),
                stateStore = RecordingStateStore(
                    PushStateV1(
                        registration = RegistrationState(
                            owner = owner,
                            accountGeneration = owner.generation,
                        ),
                    ),
                ),
                presentation = presentation,
                workScheduler = RecordingScheduler(),
                dispatcher = io,
                mainDispatcher = main,
            )
            val message = async(start = CoroutineStart.UNDISPATCHED) {
                presentationCoordinator.handleDataMessage(
                    reminderData(),
                    hasNotificationBlock = false,
                    ingressExitEpoch = presentationCoordinator.captureExitEpoch(),
                )
            }
            runCurrent()
            message.await()
            assertEquals(listOf("io"), presentationMarkers)
            presentationCoordinator.close()

            val navigationMarkers = mutableListOf<String?>()
            val navigationCoordinator = PushRegistrationCoordinator(
                authSessionRepository = RecordingAuth(AuthSession(owner.userId, AuthSession.Stage.Ready)),
                installationRepository = RecordingInstallations(),
                fcm = RecordingFirebase(),
                stateStore = RecordingStateStore(
                    PushStateV1(
                        registration = RegistrationState(
                            owner = owner,
                            accountGeneration = owner.generation,
                        ),
                        ledger = listOf(
                            LedgerRecord.OwnedReminderEvent(
                                eventId = "550e8400-e29b-41d4-a716-446655440000",
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
                ),
                workScheduler = RecordingScheduler(),
                dispatcher = io,
                mainDispatcher = main,
            )
            val tap = async(start = CoroutineStart.UNDISPATCHED) {
                navigationCoordinator.consumeTap(
                    PushTapCommand(
                        "550e8400-e29b-41d4-a716-446655440000",
                        42L,
                    ),
                    isAlreadyAtDestination = { navigationMarkers += marker.get(); false },
                    navigate = { navigationMarkers += marker.get() },
                )
            }
            runCurrent()
            assertTrue(tap.await())
            assertEquals(listOf("main", "main"), navigationMarkers)
            navigationCoordinator.close()
        }

    @Test
    fun `Firebase adapter observes Task success after canceled waiter and first terminal result wins`() =
        runTest {
            listOf(
                "register" to { messaging: FirebaseMessaging -> messaging.register() },
                "unregister" to { messaging: FirebaseMessaging -> messaging.unregister() },
            ).forEach { (operation, invoke) ->
                val messaging = mockk<FirebaseMessaging>()
                val task = mockk<Task<Void>>()
                val listener = slot<OnCompleteListener<Void>>()
                every { task.addOnCompleteListener(capture(listener)) } returns task
                every { invoke(messaging) } returns task
                val adapter = FirebaseMessagingRegistrationClient(messaging)
                val completion = if (operation == "register") {
                    adapter.register()
                } else {
                    adapter.unregister()
                }
                val waiter = async(start = CoroutineStart.UNDISPATCHED) {
                    completion.await()
                }
                waiter.cancelAndJoin()

                every { task.isSuccessful } returns true
                every { task.result } returns null
                listener.captured.onComplete(task)
                every { task.isSuccessful } returns false
                val failure = IllegalStateException("$operation-failure")
                every { task.exception } returns failure
                listener.captured.onComplete(task)

                assertEquals(Result.success(Unit), completion.await())
            }
        }

    @Test
    fun `Firebase adapter preserves first Task failure after canceled waiter`() =
        runTest {
            listOf(
                "register" to { messaging: FirebaseMessaging -> messaging.register() },
                "unregister" to { messaging: FirebaseMessaging -> messaging.unregister() },
            ).forEach { (operation, invoke) ->
                val messaging = mockk<FirebaseMessaging>()
                val task = mockk<Task<Void>>()
                val listener = slot<OnCompleteListener<Void>>()
                every { task.addOnCompleteListener(capture(listener)) } returns task
                every { invoke(messaging) } returns task
                val adapter = FirebaseMessagingRegistrationClient(messaging)
                val completion = if (operation == "register") {
                    adapter.register()
                } else {
                    adapter.unregister()
                }
                val waiter = async(start = CoroutineStart.UNDISPATCHED) {
                    completion.await()
                }
                waiter.cancelAndJoin()

                val failure = IllegalStateException("$operation-failure")
                every { task.isSuccessful } returns false
                every { task.exception } returns failure
                listener.captured.onComplete(task)
                every { task.isSuccessful } returns true
                listener.captured.onComplete(task)

                assertTrue(completion.await().exceptionOrNull() === failure)
            }
        }

    @Test
    fun `Firebase adapter handles Task completion before await for register and unregister`() =
        runTest {
            listOf(
                "register" to { messaging: FirebaseMessaging -> messaging.register() },
                "unregister" to { messaging: FirebaseMessaging -> messaging.unregister() },
            ).forEach { (operation, invoke) ->
                listOf(true, false).forEach { successful ->
                    val messaging = mockk<FirebaseMessaging>()
                    val task = mockk<Task<Void>>()
                    val listener = slot<OnCompleteListener<Void>>()
                    every { task.addOnCompleteListener(capture(listener)) } returns task
                    every { invoke(messaging) } returns task
                    every { task.isSuccessful } returns successful
                    if (successful) {
                        every { task.result } returns null
                    } else {
                        every { task.exception } returns IllegalStateException("$operation-failure")
                    }

                    val completion = if (operation == "register") {
                        FirebaseMessagingRegistrationClient(messaging).register()
                    } else {
                        FirebaseMessagingRegistrationClient(messaging).unregister()
                    }
                    listener.captured.onComplete(task)

                    val result = completion.await()
                    assertEquals(successful, result.isSuccess)
                    listener.captured.onComplete(task)
                    assertEquals(successful, completion.await().isSuccess)
                }
            }
        }

    private fun loggedOutCoordinator(
        dispatcher: CoroutineDispatcher,
        store: RecordingStateStore,
        presentation: ReminderPresentationGateway = RecordingPresentation(),
    ): PushRegistrationCoordinator =
        PushRegistrationCoordinator(
            authSessionRepository = RecordingAuth(AuthSession.LoggedOut),
            installationRepository = RecordingInstallations(),
            fcm = RecordingFirebase(),
            stateStore = store,
            presentation = presentation,
            workScheduler = RecordingScheduler(),
            dispatcher = dispatcher,
        )

    private fun TestScope.releaseDrain(coordinator: PushRegistrationCoordinator) {
        val lease = coordinator.beginAccountExitLease()
        coordinator.endAccountExit(lease)
        runCurrent()
        coordinator.onUnregistered()
        runCurrent()
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
            coordinator.onUnregistered()
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
            coordinator.onUnregistered()
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

            val activation = async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.reconcileCurrent()
            }
            runCurrent()
            coordinator.onRegistered("fid-b")
            runCurrent()
            activation.await()
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
            coordinator.onUnregistered()
            runCurrent()

            assertEquals(1, store.state.accountCleanup?.retryAttempt)
            assertEquals(
                RegistrationTerminal.MALFORMED_SUCCESS,
                store.state.accountCleanup?.terminal,
            )
            assertTrue(scheduler.enqueues > 0)

            val activation = async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.reconcileCurrent()
            }
            runCurrent()
            coordinator.onRegistered("fid-b")
            runCurrent()
            activation.await()
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
                coordinator.onUnregistered()
                runCurrent()

                assertEquals(
                    when (status) {
                        403 -> RegistrationTerminal.FORBIDDEN
                        else -> RegistrationTerminal.CONFLICT_BLOCKED
                    },
                    store.state.accountCleanup?.terminal,
                )
                assertTrue(scheduler.enqueues > 0)

                val activation = async(start = CoroutineStart.UNDISPATCHED) {
                    coordinator.reconcileCurrent()
                }
                runCurrent()
                coordinator.onRegistered("fid-b")
                runCurrent()
                activation.await()
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

    @Test
    fun `overlapping leases keep ingress closed until the final release and drain`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val coordinator = PushRegistrationCoordinator(
                authSessionRepository = RecordingAuth(
                    AuthSession(7L, AuthSession.Stage.Ready),
                ),
                installationRepository = RecordingInstallations(),
                fcm = RecordingFirebase(),
                stateStore = RecordingStateStore(PushStateV1()),
                workScheduler = RecordingScheduler(),
                dispatcher = dispatcher,
            )

            val first = coordinator.beginAccountExitLease()
            val second = coordinator.beginAccountExitLease()
            assertEquals(PushLifecyclePhase.EXITING, coordinator.lifecyclePhase)
            assertEquals(2, coordinator.activeExitLeases)

            coordinator.endAccountExit(first)
            assertEquals(PushLifecyclePhase.EXITING, coordinator.lifecyclePhase)
            assertEquals(1, coordinator.activeExitLeases)

            coordinator.endAccountExit(second)
            assertEquals(PushLifecyclePhase.DRAINING, coordinator.lifecyclePhase)
            runCurrent()
            coordinator.onUnregistered()
            runCurrent()
            assertEquals(PushLifecyclePhase.ACTIVATING_CURRENT, coordinator.lifecyclePhase)
            coordinator.close()
        }

    private class RecordingStateStore(
        var state: PushStateV1,
        private var readFailures: Int = 0,
        private var updateFailures: Int = 0,
        private val updateGate: CompletableDeferred<Unit>? = null,
    ) : PushStateStore {
        var readAttempts = 0
            private set
        var updateAttempts = 0
            private set

        override suspend fun read(): PushStateV1 {
            readAttempts++
            if (readFailures > 0) {
                readFailures--
                error("state read failed")
            }
            return state
        }

        override suspend fun update(transform: (PushStateV1) -> PushStateV1): PushStateV1 {
            updateAttempts++
            updateGate?.await()
            if (updateFailures > 0) {
                updateFailures--
                error("state update failed")
            }
            state = transform(state)
            return state
        }
    }

    private class RecordingScheduler(
        private val onEnqueue: () -> Unit = {},
        private val onCancel: () -> Unit = {},
    ) : PushWorkScheduler {
        var enqueues = 0
        var cancels = 0

        override fun enqueue() {
            enqueues++
            onEnqueue()
        }

        override fun cancel() {
            cancels++
            onCancel()
        }
    }

    private class RecordingPresentation(
        private val isCommitted: () -> Boolean = { true },
        private val onPresent: () -> Unit = {},
        private val onCancel: () -> Unit = {},
    ) : ReminderPresentationGateway {
        val presented = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        var committedAtCancellation = false

        override fun present(event: LedgerRecord.OwnedReminderEvent): Boolean {
            presented += event.eventId
            onPresent()
            return true
        }

        override fun cancel(eventIds: Collection<String>) {
            cancelled += eventIds
            committedAtCancellation = isCommitted()
            onCancel()
        }
    }

    private class RecordingFirebase(
        private val registerGate: CompletableDeferred<Unit>? = null,
        private val unregisterGate: CompletableDeferred<Result<Unit>>? = null,
    ) : FcmRegistrationClient {
        var registers = 0

        override fun register(): Deferred<Result<Unit>> {
            registers++
            val completion = CompletableDeferred<Result<Unit>>()
            if (registerGate == null) {
                completion.complete(Result.success(Unit))
            } else {
                registerGate.invokeOnCompletion { cause ->
                    if (cause == null) {
                        completion.complete(Result.success(Unit))
                    } else {
                        completion.complete(Result.failure(cause))
                    }
                }
            }
            return completion
        }

        override fun unregister(): Deferred<Result<Unit>> =
            unregisterGate ?: CompletableDeferred<Result<Unit>>().also {
                it.complete(Result.success(Unit))
            }
    }

    private class RecordingInstallations(
        deleteResults: List<Result<PushInstallationDeleteResult>> = listOf(
            Result.success(PushInstallationDeleteResult.Acknowledged),
        ),
        createGates: List<CompletableDeferred<Result<PushInstallationUpsertResult>>> = emptyList(),
    ) : PushInstallationRepository {
        private val remainingDeleteResults = deleteResults.toMutableList()
        private val remainingCreateGates = createGates.toMutableList()
        val deleted = mutableListOf<String>()
        val createdFids = mutableListOf<String>()
        var createdInstallationId: PushInstallationId? = null

        override suspend fun create(
            fid: PushInstallationFid,
        ): Result<PushInstallationUpsertResult> {
            createdFids += fid.value
            createdInstallationId = PushInstallationId(
                "550e8400-e29b-41d4-a716-446655440001",
            )
            remainingCreateGates.removeFirstOrNull()?.let { return it.await() }
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
        initialCredentialVersion: CredentialVersion = CredentialVersion("epoch", 0L),
    ) : AuthSessionRepository {
        private val state = MutableStateFlow(
            AuthCredentialState(initial, initialCredentialVersion),
        )

        override val session: StateFlow<AuthSession> =
            MutableStateFlow(initial).asStateFlow()
        override val credentialState: StateFlow<AuthCredentialState> = state.asStateFlow()

        override suspend fun read(): AuthSession = state.value.session

        fun setSession(next: AuthSession) {
            state.value = AuthCredentialState(next, state.value.credentialVersion)
        }

        fun setCredentialVersion(next: CredentialVersion) {
            state.value = AuthCredentialState(state.value.session, next)
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

    private class MarkerDispatcher(
        private val delegate: CoroutineDispatcher,
        private val marker: ThreadLocal<String?>,
        private val value: String,
    ) : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            delegate.dispatch(context) {
                marker.set(value)
                try {
                    block.run()
                } finally {
                    marker.remove()
                }
            }
        }
    }

    private companion object {
        fun acknowledgedInstallationResult(): PushInstallationUpsertResult =
            PushInstallationUpsertResult.Acknowledged(
                PushInstallation(
                    installationId = PushInstallationId(
                        "550e8400-e29b-41d4-a716-446655440001",
                    ),
                    status = PushInstallationStatus.ACTIVE,
                    lastSeenAt = Instant.EPOCH,
                ),
            )

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
