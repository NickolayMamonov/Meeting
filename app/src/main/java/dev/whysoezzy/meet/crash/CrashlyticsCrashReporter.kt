package dev.whysoezzy.meet.crash

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.whysoezzy.common.crash.CrashReporter

/** Реальный репортер (release). Подключается в Koin вместо NoOp. */
class CrashlyticsCrashReporter(
    private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance(),
) : CrashReporter {
    override fun log(message: String) = crashlytics.log(message)

    override fun recordException(throwable: Throwable) = crashlytics.recordException(throwable)

    override fun setCustomKey(key: String, value: String) = crashlytics.setCustomKey(key, value)

    override fun setUserId(id: String?) = crashlytics.setUserId(id ?: "")
}
