package dev.whysoezzy.meet.push

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

internal class PushReconcileWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val coordinator: PushRegistrationCoordinator,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result =
        if (coordinator.reconcileCurrent()) {
            Result.success()
        } else {
            Result.retry()
        }
}
