package com.whysoezzy.auth.domain

import com.whysoezzy.auth.domain.models.AuthFailure
import com.whysoezzy.auth.domain.models.AuthOutcome
import com.whysoezzy.auth.domain.models.DispatchOutcome
import com.whysoezzy.auth.domain.models.EmailAddressParser
import com.whysoezzy.auth.domain.models.EmailOtpAttempt
import com.whysoezzy.auth.domain.models.EmailOtpAttemptResult
import com.whysoezzy.auth.domain.models.EmailOtpRequestOutcome
import com.whysoezzy.auth.domain.models.EmailOtpResendOutcome
import com.whysoezzy.auth.domain.models.EmailOtpVerifyOutcome
import com.whysoezzy.auth.domain.repository.AuthRepository
import com.whysoezzy.auth.domain.repository.InMemoryPendingEmailOtpStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailOtpCoordinatorTest {
    private val repository: AuthRepository = mockk()
    private val store = InMemoryPendingEmailOtpStore()
    private var now = 1_000L
    private var nextAttemptId = 0
    private val coordinator = EmailOtpCoordinator(
        repository = repository,
        store = store,
        parser = EmailAddressParser(),
        clock = AuthClock { now },
        idGenerator = AttemptIdGenerator { "attempt-${++nextAttemptId}" },
    )

    @Test
    fun `request persists attempt before and after dispatch`() = runTest {
        coEvery { repository.requestEmailOtp("person@example.com") } returns AuthOutcome.Success(Unit)

        val result = coordinator.request(" Person@Example.com ")

        assertTrue(result is EmailOtpRequestOutcome.ProceedToVerification)
        val loaded = coordinator.load("attempt-1")
        assertTrue(loaded is EmailOtpAttemptResult.Found)
        assertEquals("p***@example.com", (loaded as EmailOtpAttemptResult.Found).attempt.maskedEmail)
    }

    @Test
    fun `active attempt recovers and authenticated cleanup clears it`() = runTest {
        coEvery { repository.requestEmailOtp(any()) } returns AuthOutcome.Success(Unit)
        coordinator.request("person@example.com")

        assertTrue(coordinator.loadActive() is EmailOtpAttemptResult.Found)
        coordinator.clearActive()
        assertEquals(EmailOtpAttemptResult.MissingOrExpired, coordinator.loadActive())
    }

    @Test
    fun `expired attempt is removed and resend reports missing`() = runTest {
        coEvery { repository.requestEmailOtp(any()) } returns AuthOutcome.Success(Unit)
        coordinator.request("person@example.com")
        now += 15 * 60_000L

        assertEquals(EmailOtpAttemptResult.MissingOrExpired, coordinator.loadActive())
        assertEquals(
            EmailOtpResendOutcome.Failed(null, AuthFailure.MissingOrExpiredAttempt),
            coordinator.resend("attempt-1"),
        )
    }

    @Test
    fun `resend rate limit keeps active attempt without resetting expiry`() = runTest {
        coEvery { repository.requestEmailOtp(any()) } returnsMany listOf(
            AuthOutcome.Success(Unit),
            AuthOutcome.Failure(AuthFailure.RateLimited),
        )
        coordinator.request("person@example.com")
        now += 60_000L

        val result = coordinator.resend("attempt-1")

        assertTrue(result is EmailOtpResendOutcome.Failed)
        assertTrue((result as EmailOtpResendOutcome.Failed).attempt != null)
        assertTrue(coordinator.loadActive() is EmailOtpAttemptResult.Found)
    }

    @Test
    fun `persisted no challenge attempt is restored during recovery`() = runTest {
        coEvery { repository.requestEmailOtp(any()) } returns
            AuthOutcome.Failure(AuthFailure.RateLimited)

        val result = coordinator.request("person@example.com")

        assertTrue(result is EmailOtpRequestOutcome.StayOnEmail)
        assertEquals(
            EmailOtpAttemptResult.RecoverOnEmail(
                email = "person@example.com",
                attempt = (result as EmailOtpRequestOutcome.StayOnEmail).attempt,
                failure = AuthFailure.RateLimited,
            ),
            coordinator.loadActive(),
        )
    }

    @Test
    fun `verification does not call transport for a known no challenge attempt`() = runTest {
        coEvery { repository.requestEmailOtp(any()) } returns
            AuthOutcome.Failure(AuthFailure.RateLimited)
        coordinator.request("person@example.com")

        val result = coordinator.verify("attempt-1", "123456")

        assertEquals(
            EmailOtpVerifyOutcome.Failed(AuthFailure.MissingOrExpiredAttempt),
            result,
        )
        coVerify(exactly = 0) {
            repository.verifyEmailOtp(any(), any(), any(), any())
        }
    }

    @Test
    fun `failed resend persists cooldown and rejects another resend before deadline`() = runTest {
        coEvery { repository.requestEmailOtp(any()) } returnsMany listOf(
            AuthOutcome.Success(Unit),
            AuthOutcome.Failure(AuthFailure.RateLimited),
        )
        coordinator.request("person@example.com")
        now += 60_000L

        val first = coordinator.resend("attempt-1")
        assertTrue(first is EmailOtpResendOutcome.Failed)
        val firstFailure = first as EmailOtpResendOutcome.Failed
        val deadline = firstFailure.attempt!!.resendAvailableAtEpochMillis

        now += 1L
        val second = coordinator.resend("attempt-1")

        assertEquals(
            EmailOtpResendOutcome.Failed(
                firstFailure.attempt,
                AuthFailure.ResendNotAvailable(deadline),
            ),
            second,
        )
        coVerify(exactly = 2) { repository.requestEmailOtp("person@example.com") }
    }

    @Test
    fun `concurrent requests are serialized and newest generation remains active`() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        coEvery { repository.requestEmailOtp("first@example.com") } coAnswers {
            firstStarted.complete(Unit)
            releaseFirst.await()
            AuthOutcome.Success(Unit)
        }
        coEvery { repository.requestEmailOtp("second@example.com") } returns AuthOutcome.Success(Unit)

        val first = async { coordinator.request("first@example.com") }
        firstStarted.await()
        val second = async { coordinator.request("second@example.com") }

        assertTrue(!second.isCompleted)
        releaseFirst.complete(Unit)
        first.await()
        second.await()

        val active = coordinator.loadActive() as EmailOtpAttemptResult.Found
        assertEquals("attempt-2", active.attempt.attemptId)
        assertEquals("s***@example.com", active.attempt.maskedEmail)
    }

    @Test
    fun `stale request completion resolves to the newer active generation`() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()
        coEvery { repository.requestEmailOtp("first@example.com") } coAnswers {
            firstStarted.complete(Unit)
            releaseFirst.await()
            AuthOutcome.Success(Unit)
        }
        coEvery { repository.requestEmailOtp("second@example.com") } coAnswers {
            secondStarted.complete(Unit)
            releaseSecond.await()
            AuthOutcome.Success(Unit)
        }

        val first = async { coordinator.request("first@example.com") }
        firstStarted.await()
        val second = async { coordinator.request("second@example.com") }
        secondStarted.await()
        releaseFirst.complete(Unit)

        val firstResult = first.await()
        releaseSecond.complete(Unit)
        second.await()

        assertTrue(firstResult is EmailOtpRequestOutcome.ProceedToVerification)
        assertEquals(
            "attempt-2",
            (firstResult as EmailOtpRequestOutcome.ProceedToVerification).attempt.attemptId,
        )
        assertEquals(
            DispatchOutcome.Unconfirmed,
            firstResult.attempt.dispatchOutcome,
        )
    }

    @Test
    fun `stale resend completion resolves to the newer generation`() = runTest {
        coEvery { repository.requestEmailOtp(any()) } returns AuthOutcome.Success(Unit)
        coordinator.request("person@example.com")
        now += 60_000L

        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()
        var dispatchCount = 0
        coEvery { repository.requestEmailOtp("person@example.com") } coAnswers {
            dispatchCount += 1
            if (dispatchCount == 1) {
                firstStarted.complete(Unit)
                releaseFirst.await()
            } else {
                secondStarted.complete(Unit)
                releaseSecond.await()
            }
            AuthOutcome.Success(Unit)
        }

        val first = async { coordinator.resend("attempt-1") }
        firstStarted.await()
        now += 60_000L
        val second = async { coordinator.resend("attempt-1") }
        secondStarted.await()
        releaseFirst.complete(Unit)

        val firstResult = first.await()
        releaseSecond.complete(Unit)
        second.await()

        assertTrue(firstResult is EmailOtpResendOutcome.Unconfirmed)
        assertEquals(
            "attempt-1",
            (firstResult as EmailOtpResendOutcome.Unconfirmed).attempt.attemptId,
        )
        assertEquals(
            DispatchOutcome.Unconfirmed,
            firstResult.attempt.dispatchOutcome,
        )
    }

    @Test
    fun `stale resend completion resolves to a newer active attempt`() = runTest {
        val resendStarted = CompletableDeferred<Unit>()
        val releaseResend = CompletableDeferred<Unit>()
        var callCount = 0
        coEvery { repository.requestEmailOtp(any()) } coAnswers {
            when (++callCount) {
                1 -> AuthOutcome.Success(Unit)
                2 -> {
                    resendStarted.complete(Unit)
                    releaseResend.await()
                    AuthOutcome.Success(Unit)
                }
                else -> AuthOutcome.Success(Unit)
            }
        }

        coordinator.request("person@example.com")
        now += 60_000L
        val resend = async { coordinator.resend("attempt-1") }
        resendStarted.await()

        val replacement = async { coordinator.request("new@example.com") }
        replacement.await()
        releaseResend.complete(Unit)

        val result = resend.await()
        assertEquals(
            EmailOtpResendOutcome.Confirmed(
                (coordinator.loadActive() as EmailOtpAttemptResult.Found).attempt,
            ),
            result,
        )
        assertEquals(
            "attempt-2",
            (result as EmailOtpResendOutcome.Confirmed).attempt.attemptId,
        )
    }

    @Test
    fun `stale request completion treats expired winning attempt as missing`() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()
        coEvery { repository.requestEmailOtp("first@example.com") } coAnswers {
            firstStarted.complete(Unit)
            releaseFirst.await()
            AuthOutcome.Success(Unit)
        }
        coEvery { repository.requestEmailOtp("second@example.com") } coAnswers {
            secondStarted.complete(Unit)
            releaseSecond.await()
            AuthOutcome.Success(Unit)
        }

        val first = async { coordinator.request("first@example.com") }
        firstStarted.await()
        val second = async { coordinator.request("second@example.com") }
        secondStarted.await()
        now += 15 * 60_000L + 1L

        releaseFirst.complete(Unit)
        assertEquals(
            EmailOtpRequestOutcome.StayOnEmail(
                attempt = EmailOtpAttempt(
                    attemptId = "",
                    maskedEmail = "",
                    resendAvailableAtEpochMillis = 0L,
                    challengeMayBeActive = false,
                    dispatchOutcome = DispatchOutcome.RejectedValidation,
                ),
                failure = AuthFailure.MissingOrExpiredAttempt,
            ),
            first.await(),
        )

        releaseSecond.complete(Unit)
        assertEquals(
            EmailOtpRequestOutcome.StayOnEmail(
                attempt = EmailOtpAttempt(
                    attemptId = "",
                    maskedEmail = "",
                    resendAvailableAtEpochMillis = 0L,
                    challengeMayBeActive = false,
                    dispatchOutcome = DispatchOutcome.RejectedValidation,
                ),
                failure = AuthFailure.MissingOrExpiredAttempt,
            ),
            second.await(),
        )
    }

    @Test
    fun `clear waits for an in-flight request and cannot be overwritten by its completion`() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val releaseRequest = CompletableDeferred<Unit>()
        coEvery { repository.requestEmailOtp(any()) } coAnswers {
            requestStarted.complete(Unit)
            releaseRequest.await()
            AuthOutcome.Success(Unit)
        }

        val request = async { coordinator.request("person@example.com") }
        requestStarted.await()
        val clear = async { coordinator.clearActive() }

        assertTrue(!clear.isCompleted)
        releaseRequest.complete(Unit)
        request.await()
        clear.await()

        assertEquals(EmailOtpAttemptResult.MissingOrExpired, coordinator.loadActive())
    }
}
