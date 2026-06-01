package com.whysoezzy.common.crash

/** Заглушка: ничего не отправляет. Дефолт для debug и тестов. */
class NoOpCrashReporter : CrashReporter {
    override fun log(message: String) = Unit

    override fun recordException(throwable: Throwable) = Unit

    override fun setCustomKey(key: String, value: String) = Unit

    override fun setUserId(id: String?) = Unit
}
