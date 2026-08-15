package dev.whysoezzy.meet.push

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.context.GlobalContext

class PushReconcileWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        GlobalContext.get().get<PushRegistrationCoordinator>().reconcileCurrent()
        return Result.success()
    }
}
