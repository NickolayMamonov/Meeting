package dev.whysoezzy.meet.crash

import com.whysoezzy.common.crash.CrashReporter
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class CrashReportingTreeTest {
    private val reporter = mockk<CrashReporter>(relaxed = true)
    private lateinit var tree: CrashReportingTree

    @Before
    fun setUp() {
        tree = CrashReportingTree(reporter)
        Timber.plant(tree)
    }

    @After
    fun tearDown() {
        Timber.uproot(tree)
    }

    @Test
    fun `WARN forwarded as breadcrumb, no exception`() {
        Timber.tag("Tag").w("warn msg")
        verify { reporter.log("[Tag] warn msg") }
        verify(exactly = 0) { reporter.recordException(any()) }
    }

    @Test
    fun `ERROR with throwable records exception`() {
        val ex = RuntimeException("boom")
        Timber.tag("Tag").e(ex, "err msg")

        verify { reporter.log(match { it.startsWith("[Tag] err msg") }) }
        verify { reporter.recordException(ex) }
    }

    @Test
    fun `INFO below WARN threshold - nothing forwarded`() {
        Timber.tag("Tag").i("info msg")
        verify(exactly = 0) { reporter.log(any()) }
        verify(exactly = 0) { reporter.recordException(any()) }
    }
}
