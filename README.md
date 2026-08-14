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

Release-сборка принимает только production origin
`https://api.whysoezzy.online`. Android release networking uses the platform
system CA store, disables cleartext traffic, and has no certificate or public
key pin contract.

```sh
./gradlew assembleRelease -PBASE_URL_RELEASE=https://api.whysoezzy.online
```

In CI, configure the non-secret `BASE_URL_RELEASE` variable on the protected
`android-release` environment:

```yaml
env:
  ORG_GRADLE_PROJECT_BASE_URL_RELEASE: ${{ vars.BASE_URL_RELEASE }}
```

The release build fails closed for any other value, including HTTP, paths,
queries, fragments, credentials, ports, placeholders, or whitespace changes.
Debug and snapshot variants retain their existing local-backend behavior.
