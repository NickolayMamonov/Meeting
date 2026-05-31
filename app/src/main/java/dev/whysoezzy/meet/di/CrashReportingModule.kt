package dev.whysoezzy.meet.di

import com.whysoezzy.common.crash.CrashReporter
import com.whysoezzy.common.crash.NoOpCrashReporter
import dev.whysoezzy.meet.BuildConfig
import dev.whysoezzy.meet.crash.CrashlyticsCrashReporter
import org.koin.dsl.module

val crashReportingModule = module {
    single<CrashReporter> {
        if (BuildConfig.DEBUG) NoOpCrashReporter() else CrashlyticsCrashReporter()
    }
}
