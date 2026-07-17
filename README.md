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
2. При необходимости задай URL debug-сервера через `BASE_URL_DEBUG` в `~/.gradle/gradle.properties` или `-PBASE_URL_DEBUG=http://...`
3. `./gradlew assembleDebug`

## Release BASE_URL

Release-сборка требует явный HTTPS URL в `BASE_URL_RELEASE`; значение не хранится в репозитории:

```sh
./gradlew assembleRelease -PBASE_URL_RELEASE=https://release-test.invalid
```

В CI передавай защищённый секрет без вывода в логи через Gradle project property:

```yaml
env:
  ORG_GRADLE_PROJECT_BASE_URL_RELEASE: ${{ secrets.BASE_URL_RELEASE }}
```

Пустые значения, HTTP и `https://api.example.com` отклоняются. Реальный release URL и настройки TLS/pinning должны быть согласованы с production-конфигурацией сети.
