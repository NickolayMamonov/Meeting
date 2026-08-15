package dev.whysoezzy.meet.push

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

internal object PushWorkScheduler {
    const val UNIQUE_WORK_NAME = "push-registration-reconcile"

    fun enqueue(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
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

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
