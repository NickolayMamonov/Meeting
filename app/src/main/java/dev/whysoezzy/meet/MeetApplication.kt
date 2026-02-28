package dev.whysoezzy.meet

import android.app.Application
import com.whysoezzy.data.di.communitiesModule
import com.whysoezzy.data.di.meetingsModule
import com.whysoezzy.data.di.profileDataModule
import dev.whysoezzy.auth.di.authFeatureModule
import dev.whysoezzy.auth.di.authModule
import dev.whysoezzy.communities.di.communityModule
import dev.whysoezzy.meetings.di.mainFeatureModule
import dev.whysoezzy.profile.di.profileFeatureModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class MeetApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger(Level.DEBUG)
            androidContext(this@MeetApplication)
            modules(
                authModule,
                authFeatureModule,
                meetingsModule,
                communitiesModule,
                profileDataModule,
                mainFeatureModule,
                communityModule,
                profileDataModule,
                profileFeatureModule
            )
        }
    }
}