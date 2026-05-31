package dev.whysoezzy.meet.di

import com.whysoezzy.common.crash.CrashReporter
import com.whysoezzy.common.crash.NoOpCrashReporter
import org.koin.dsl.module

val crashReportingModule = module {
    single<CrashReporter> { NoOpCrashReporter() }
}
