package com.whysoezzy.common.crash

/**
 * Vendor-agnostic фасад крэш-репортинга (R-056).
 * Реализации: NoOpCrashReporter (debug/тесты), CrashlyticsCrashReporter (release).
 */
interface CrashReporter {
    fun log(message: String)

    fun recordException(throwable: Throwable)

    fun setCustomKey(key: String, value: String)

    fun setUserId(id: String?)
}
