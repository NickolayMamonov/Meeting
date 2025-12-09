package dev.whysoezzy.meetings.data

import dev.whysoezzy.data.repository.AdBlockRepositoryImpl
import dev.whysoezzy.data.repository.CommunitiesRepositoryImpl
import dev.whysoezzy.data.repository.MeetingsRepositoryImpl
import dev.whysoezzy.domain.repository.AdBlockRepository
import dev.whysoezzy.domain.repository.CommunitiesRepository
import dev.whysoezzy.domain.repository.MeetingsRepository
import org.koin.dsl.module

/**
 * Конфигурация для переключения между mock и реальными данными
 */
object DataSourceConfig {
    
    // Флаги для выбора источника данных
    var useMockData = true
    var useMockError = false
    var useMockEmpty = false
    var useMockSlow = false
    
    /**
     * Переключает на использование mock данных
     */
    fun enableMockData() {
        useMockData = true
        useMockError = false
        useMockEmpty = false
        useMockSlow = false
    }
    
    /**
     * Переключает на использование реальных API
     */
    fun enableRealData() {
        useMockData = false
        useMockError = false
        useMockEmpty = false
        useMockSlow = false
    }
    
    /**
     * Включает тестирование ошибок
     */
    fun enableErrorScenario() {
        useMockData = true
        useMockError = true
        useMockEmpty = false
        useMockSlow = false
    }
    
    /**
     * Включает тестирование пустых данных
     */
    fun enableEmptyScenario() {
        useMockData = true
        useMockError = false
        useMockEmpty = true
        useMockSlow = false
    }
    
    /**
     * Включает тестирование медленной загрузки
     */
    fun enableSlowScenario() {
        useMockData = true
        useMockError = false
        useMockEmpty = false
        useMockSlow = true
    }
}

/**
 * Koin модуль для переключаемых репозиториев
 */
val mockDataModule = module {
    
    // Meetings Repository
    single<MeetingsRepository> {
        when {
            !DataSourceConfig.useMockData -> {
                // Используем реальный репозиторий
                MeetingsRepositoryImpl(get(), get())
            }
            DataSourceConfig.useMockError -> {
                // Тестируем ошибки
                MockScenarios.ErrorMeetingsRepository()
            }
            DataSourceConfig.useMockEmpty -> {
                // Тестируем пустые данные
                MockScenarios.EmptyMeetingsRepository()
            }
            else -> {
                // Обычные mock данные
                MockMeetingsRepository()
            }
        }
    }
    
    // Communities Repository
    single<CommunitiesRepository> {
        if (DataSourceConfig.useMockData) {
            MockCommunitiesRepository()
        } else {
            CommunitiesRepositoryImpl()
        }
    }
    
    // AdBlock Repository
    single<AdBlockRepository> {
        if (DataSourceConfig.useMockData) {
            MockAdBlockRepository()
        } else {
            AdBlockRepositoryImpl()
        }
    }
}

/**
 * Расширения для быстрого переключения сценариев
 */
object QuickScenarios {
    
    /**
     * Демо данные для презентации
     */
    fun demo() {
        DataSourceConfig.enableMockData()
    }
    
    /**
     * Тестирование обработки ошибок
     */
    fun errorTesting() {
        DataSourceConfig.enableErrorScenario()
    }
    
    /**
     * Тестирование пустых состояний
     */
    fun emptyStateTesting() {
        DataSourceConfig.enableEmptyScenario()
    }
    
    /**
     * Тестирование производительности
     */
    fun performanceTesting() {
        DataSourceConfig.enableSlowScenario()
    }
    
    /**
     * Продакшн режим
     */
    fun production() {
        DataSourceConfig.enableRealData()
    }
}