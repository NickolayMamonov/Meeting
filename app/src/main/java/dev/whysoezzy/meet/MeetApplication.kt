package dev.whysoezzy.meet

import android.app.Application
import dev.whysoezzy.domain.di.domainModule
import dev.whysoezzy.meetings.data.QuickScenarios
import dev.whysoezzy.meetings.di.mainFeatureModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class MeetApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()

        // Настраиваем сценарий данных
        // Можно переключать между:
        // QuickScenarios.demo() - обычные mock данные
        // QuickScenarios.errorTesting() - тестирование ошибок
        // QuickScenarios.emptyStateTesting() - тестирование пустых состояний
        // QuickScenarios.performanceTesting() - тестирование медленной загрузки
        // QuickScenarios.production() - реальные API

        QuickScenarios.demo() // По умолчанию используем mock данные
        
        startKoin {
            androidLogger(Level.DEBUG)
            androidContext(this@MeetApplication)
            modules(
                domainModule,
                mainFeatureModule
            )
        }
    }
}