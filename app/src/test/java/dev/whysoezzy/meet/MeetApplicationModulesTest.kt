package dev.whysoezzy.meet

import com.whysoezzy.common.crash.CrashReporter
import com.whysoezzy.common.crash.NoOpCrashReporter
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.dsl.koinApplication

class MeetApplicationModulesTest {
    @Test
    fun `debug crash reporter is no-op`() {
        val isolatedKoin = koinApplication {
            modules(meetApplicationModules)
        }

        try {
            assertTrue(isolatedKoin.koin.get<CrashReporter>() is NoOpCrashReporter)
        } finally {
            isolatedKoin.close()
        }
    }
}
