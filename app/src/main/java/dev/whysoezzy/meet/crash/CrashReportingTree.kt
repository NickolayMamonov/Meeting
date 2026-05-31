package dev.whysoezzy.meet.crash

import android.util.Log
import com.whysoezzy.common.crash.CrashReporter
import timber.log.Timber

/**
 * Форвардит не-debug логи в CrashReporter (R-056, вариант breadcrumbs):
 *  - WARN/ERROR → log() как breadcrumb
 *  - ERROR с throwable → recordException()
 * VERBOSE/DEBUG/INFO игнорируются (остаются в DebugTree).
 */
class CrashReportingTree(
    private val crashReporter: CrashReporter,
) : Timber.Tree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean =
        priority >= Log.WARN

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        crashReporter.log(if (tag != null) "[$tag] $message" else message)
        if (priority >= Log.ERROR && t != null) {
            crashReporter.recordException(t)
        }
    }
}
