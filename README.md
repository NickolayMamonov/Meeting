# Meeting App

Pet-проект: приложение для поиска и посещения встреч и сообществ.

## Стек
- Kotlin 2.0 · Jetpack Compose · Koin · Ktor · Coroutines/Flow · Coil
- Clean Architecture: data / domain / presentation

## Модули
- `:app` — точка входа, навигация
- `:core:auth` — аутентификация, токены
- `:core:network` — Ktor-клиент, safeApiCall
- `:core:domain` — общие domain-модели
- `:core:data` — общие DTO и маппинг
- `:core:common` — утилиты (DateUtils, ValidationUtils, ErrorMessages)
- `:uikit` — компоненты, UI-модели, тема
- `:features:meetings` — встречи (data / domain / presentation)
- `:features:communities` — сообщества (data / domain / presentation)
- `:features:profile` — профиль пользователя (data / domain / presentation)
- `:features:auth` — экраны авторизации

## Запуск
1. Запусти бэкенд-сервер (см. репозиторий бэкенда)
2. Убедись что `BASE_URL` в `core/network/build.gradle.kts` указывает на твой сервер
3. `./gradlew assembleDebug`
