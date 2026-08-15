package dev.whysoezzy.meet

import android.app.Application
import com.whysoezzy.auth.di.authModule
import com.whysoezzy.common.crash.CrashReporter
import com.whysoezzy.data.di.communitiesModule
import com.whysoezzy.data.di.meetingsModule
import com.whysoezzy.data.di.profileDataModule
import dev.whysoezzy.auth.di.authFeatureModule
import dev.whysoezzy.communities.di.communityModule
import dev.whysoezzy.meet.crash.CrashReportingTree
import dev.whysoezzy.meet.di.appGlueModule
import dev.whysoezzy.meet.di.appModule
import dev.whysoezzy.meet.di.pushRegistrationModule
import dev.whysoezzy.meet.push.PushRegistrationCoordinator
import dev.whysoezzy.meetings.di.mainFeatureModule
import dev.whysoezzy.profile.di.profileFeatureModule
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import timber.log.Timber

class MeetApplication : Application() {
    private val crashReporter: CrashReporter by inject()
    private val pushRegistrationCoordinator: PushRegistrationCoordinator by inject()

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.ERROR)
            androidContext(this@MeetApplication)
            modules(
                appModule,
                appGlueModule,
                authModule,
                pushRegistrationModule,
                authFeatureModule,
                meetingsModule,
                communitiesModule,
                profileDataModule,
                mainFeatureModule,
                communityModule,
                profileFeatureModule,
            )
        }

        pushRegistrationCoordinator.start()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(CrashReportingTree(crashReporter))
        }
    }
}
