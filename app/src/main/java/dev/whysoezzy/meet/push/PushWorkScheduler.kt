package dev.whysoezzy.meet.push

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

internal interface PushWorkScheduler {
    fun enqueue()

    fun cancel()
}

internal class AndroidPushWorkScheduler(
    context: Context,
) : PushWorkScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun enqueue() {
        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<PushReconcileWorker>()
                .setConstraints(
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                ).build(),
        )
    }

    override fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private companion object {
        const val UNIQUE_WORK_NAME = "push-registration-reconcile"
    }
}

internal object NoOpPushWorkScheduler : PushWorkScheduler {
    override fun enqueue() = Unit

    override fun cancel() = Unit
}
