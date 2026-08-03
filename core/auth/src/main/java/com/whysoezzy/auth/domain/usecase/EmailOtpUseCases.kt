package com.whysoezzy.auth.domain.usecase

import com.whysoezzy.auth.domain.EmailOtpCoordinator
import com.whysoezzy.auth.domain.models.EmailOtpAttemptResult
import com.whysoezzy.auth.domain.models.EmailOtpRequestOutcome
import com.whysoezzy.auth.domain.models.EmailOtpResendOutcome
import com.whysoezzy.auth.domain.models.EmailOtpVerifyOutcome

class RequestEmailOtpUseCase(
    private val coordinator: EmailOtpCoordinator,
) {
    suspend operator fun invoke(email: String): EmailOtpRequestOutcome = coordinator.request(email)
}

class RecoverEmailOtpAttemptUseCase(
    private val coordinator: EmailOtpCoordinator,
) {
    suspend operator fun invoke(attemptId: String): EmailOtpAttemptResult = coordinator.load(attemptId)

    suspend operator fun invoke(): EmailOtpAttemptResult = coordinator.loadActive()
}

class LoadEmailOtpAttemptUseCase(
    private val coordinator: EmailOtpCoordinator,
) {
    suspend operator fun invoke(attemptId: String): EmailOtpAttemptResult = coordinator.load(attemptId)
}

class ResendEmailOtpUseCase(
    private val coordinator: EmailOtpCoordinator,
) {
    suspend operator fun invoke(attemptId: String): EmailOtpResendOutcome = coordinator.resend(attemptId)
}

class VerifyEmailOtpUseCase(
    private val coordinator: EmailOtpCoordinator,
) {
    suspend operator fun invoke(
        attemptId: String,
        code: String,
        name: String? = null,
        surname: String? = null,
    ): EmailOtpVerifyOutcome = coordinator.verify(attemptId, code, name, surname)
}

class ClearEmailOtpAttemptUseCase(
    private val coordinator: EmailOtpCoordinator,
) {
    suspend operator fun invoke(attemptId: String) {
        coordinator.clear(attemptId)
    }
}

class ClearPendingEmailOtpUseCase(
    private val coordinator: EmailOtpCoordinator,
) {
    suspend operator fun invoke() = coordinator.clearActive()
}

class LoadActiveEmailOtpAttemptUseCase(
    private val coordinator: EmailOtpCoordinator,
) {
    suspend operator fun invoke(): EmailOtpAttemptResult = coordinator.loadActive()
}
