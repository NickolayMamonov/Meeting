package dev.whysoezzy.meet.push

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import org.koin.core.context.GlobalContext

internal class PushReconcileWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val coordinator: PushRegistrationCoordinator,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return runCatching {
            if (coordinator.reconcileCurrent()) Result.success() else Result.retry()
        }.getOrElse { Result.retry() }
    }
}

internal class PushReconcileWorkerFactory : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        if (workerClassName != PushReconcileWorker::class.qualifiedName) return null
        return PushReconcileWorker(
            appContext = appContext,
            workerParams = workerParameters,
            coordinator = GlobalContext.get().get(),
        )
    }
}
