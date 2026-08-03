package com.whysoezzy.auth.domain

import com.whysoezzy.auth.domain.models.AuthFailure
import com.whysoezzy.auth.domain.models.AuthOutcome
import com.whysoezzy.auth.domain.models.EmailAddressParser
import com.whysoezzy.auth.domain.models.EmailOtpAttemptResult
import com.whysoezzy.auth.domain.models.EmailOtpRequestOutcome
import com.whysoezzy.auth.domain.models.EmailOtpResendOutcome
import com.whysoezzy.auth.domain.repository.AuthRepository
import com.whysoezzy.auth.domain.repository.InMemoryPendingEmailOtpStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailOtpCoordinatorTest {
    private val repository: AuthRepository = mockk()
    private val store = InMemoryPendingEmailOtpStore()
    private var now = 1_000L
    private val coordinator = EmailOtpCoordinator(
        repository = repository,
        store = store,
        parser = EmailAddressParser(),
        clock = AuthClock { now },
        idGenerator = AttemptIdGenerator { "attempt-1" },
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
    fun `persisted no challenge attempt is cleared during recovery`() = runTest {
        coEvery { repository.requestEmailOtp(any()) } returns
            AuthOutcome.Failure(AuthFailure.RateLimited)

        val result = coordinator.request("person@example.com")

        assertTrue(result is EmailOtpRequestOutcome.StayOnEmail)
        assertEquals(EmailOtpAttemptResult.MissingOrExpired, coordinator.loadActive())
        assertEquals(EmailOtpAttemptResult.MissingOrExpired, coordinator.load("attempt-1"))
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
}
