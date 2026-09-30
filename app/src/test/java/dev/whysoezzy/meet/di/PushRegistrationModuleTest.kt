package dev.whysoezzy.meet.di

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import dev.whysoezzy.meet.push.PushReconcileWorker
import dev.whysoezzy.meet.push.PushRegistrationCoordinator
import io.mockk.mockk
import org.junit.Assert.assertSame
import org.junit.Test
import org.koin.android.ext.koin.androidContext
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class PushRegistrationModuleTest {
    @Test
    fun `push reconcile worker resolves with WorkerParameters and configured androidContext`() {
        val context = mockk<Context>(relaxed = true)
        val workerParameters = mockk<WorkerParameters>(relaxed = true)
        val coordinator = mockk<PushRegistrationCoordinator>()
        val isolatedKoin = koinApplication {
            androidContext(context)
            modules(
                pushRegistrationModule,
                module {
                    single<PushRegistrationCoordinator> { coordinator }
                },
            )
        }

        try {
            val worker = isolatedKoin.koin.get<ListenableWorker>(named(PushReconcileWorker::class.java.name)) {
                parametersOf(workerParameters)
            }

            assertSame(context, worker.applicationContext)
            assertSame(PushReconcileWorker::class.java, worker::class.java)
        } finally {
            isolatedKoin.close()
        }
    }
}
