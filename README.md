# Meet Android

Meet is an Android app for discovering and attending meetings and communities.
This README is the contributor entry point for the Android project.

## Stack and architecture

- Kotlin 2.3.20, Jetpack Compose, Koin, Ktor, and Kotlin Coroutines/Flow.
- Clean Architecture with feature `data`, `domain`, and `presentation`
  boundaries.
- Gradle convention plugins in `build-logic/convention` centralize Android,
  Kotlin, Compose, JVM, serialization, and publishing configuration.
- The version catalog in `gradle/libs.versions.toml` is the dependency source
  of truth.

Feature data modules own API and repository implementations. Feature domain
modules expose contracts and use cases. Feature presentation modules render
Compose UI and consume domain contracts; presentation does not reach into
`:core:network`. Shared networking, authentication, data, domain, common
utilities, testing, and UI components remain in their respective core modules.

The convention plugins use JDK 21, compile SDK 36, and minimum SDK 30 for
Android modules. The application enables Compose, Koin, Firebase Services, and
Crashlytics through the existing project configuration.

## Modules

The following map mirrors the 18 module paths in `settings.gradle.kts`:

### Application and shared modules

- `:app` — application entry point, dependency assembly, and navigation.
- `:uikit` — shared Compose components, UI models, theme, and assets.
- `:core:common` — shared JVM utilities.
- `:core:network` — Ktor client and the `BuildConfig.BASE_URL` boundary.
- `:core:auth` — authentication, token storage, and authenticated state.
- `:core:domain` — shared domain contracts and models.
- `:core:data` — shared DTOs and mapping support.
- `:core:testing` — shared test fixtures and coroutine test utilities.

### Feature modules

- `:features:auth` — authentication screens and presentation.
- `:features:meetings:data` — meetings data sources and repositories.
- `:features:meetings:domain` — meetings domain contracts and use cases.
- `:features:meetings:presentation` — meetings Compose UI.
- `:features:communities:data` — communities data sources and repositories.
- `:features:communities:domain` — communities domain contracts and use cases.
- `:features:communities:presentation` — communities Compose UI.
- `:features:profile:data` — profile data sources and repositories.
- `:features:profile:domain` — profile domain contracts and use cases.
- `:features:profile:presentation` — profile Compose UI.

## Prerequisites

1. Install a JDK 21 distribution and make it the JDK used by Gradle and
   Android Studio.
2. Install and configure the Android SDK through Android Studio. The project
   compiles against SDK 36 and supports Android API 30 and newer.
3. Create the ignored, machine-local `local.properties` file at the
   repository root. Point `sdk.dir` at the Android SDK installation, for
   example:

   ```properties
   sdk.dir=/absolute/path/to/Android/Sdk
   ```

   On Windows, use the Android SDK path from Android Studio, for example
   `sdk.dir=C:\\Users\\<user>\\AppData\\Local\\Android\\Sdk`.
4. Start the local backend separately. For an Android Emulator, the default
   debug origin is `http://10.0.2.2:8080`, which maps to port 8080 on the
   development machine. To select another local, LAN, or deployed debug
   origin, pass `-PBASE_URL_DEBUG=<origin>` to the Gradle command.

## Non-production Firebase bootstrap

The repository contains two deliberately different Firebase files:

- `app/google-services-ci.json` is a tracked, non-secret fixture. It exists
  for local debug and CI-equivalent checks only.
- `app/google-services.json` is the generated/local destination. It is ignored
  by Git and must remain uncommitted.

Before any Gradle command, provision the ignored destination from the fixture
when running local debug or CI-equivalent checks. These commands are
non-production bootstrap only:

**POSIX (macOS, Linux, or Git Bash):**

```sh
cp app/google-services-ci.json app/google-services.json
```

**Windows PowerShell:**

```powershell
Copy-Item -Path app/google-services-ci.json -Destination app/google-services.json
```

Do not commit `app/google-services.json`. The tracked fixture is not protected
snapshot or release Firebase configuration and must never be used as a
substitute for protected release inputs. Protected Firebase configuration,
keystores, passwords, and signing inputs stay in release custody.

## Run a debug build

After the non-production Firebase bootstrap above, open the project in Android
Studio, select the `app` run configuration, choose an emulator or connected
device, and launch the `debug` variant. The equivalent command-line build is:

**POSIX:**

```sh
./gradlew assembleDebug
```

**Windows PowerShell:**

```powershell
.\gradlew.bat assembleDebug
```

To use a different backend, append the property to either command:

```text
-PBASE_URL_DEBUG=<origin>
```

For example, a complete POSIX invocation is
`./gradlew assembleDebug -PBASE_URL_DEBUG=http://192.168.1.10:8080`.

## Variants

- **Debug** is the local development variant. It uses
  `BASE_URL_DEBUG`, defaulting to `http://10.0.2.2:8080`, and is suitable for
  Android Studio or command-line development.
- **Snapshot** is the dev snapshot variant. It inherits debug behavior and
  uses protected snapshot Firebase and signing inputs in the snapshot release
  workflow; the non-secret fixture above is not snapshot configuration.
- **Release** is the stable production variant. Its release network origin
  must be the exact repository-published, non-secret value
  `https://api.whysoezzy.online`. Protected Firebase configuration and signing
  material are supplied only through release custody.

See [Android release operations](docs/android-release-operations.md) for the
authoritative promotion, custody, evidence, signing, and publication
procedures. This README does not represent an unfinished snapshot, beta, or
stable release as shipped.

## CI checks

The repository CI runs these checks after provisioning its non-secret fixture:

**POSIX:**

```sh
./gradlew ktlintCheck
./gradlew :app:lint
./gradlew testDebugUnitTest
python -m unittest discover -s scripts/release -p "test_*.py"
./gradlew assembleDebug
```

**Windows PowerShell equivalents for Gradle commands:**

```powershell
.\gradlew.bat ktlintCheck
.\gradlew.bat :app:lint
.\gradlew.bat testDebugUnitTest
python -m unittest discover -s scripts/release -p "test_*.py"
.\gradlew.bat assembleDebug
```

These are static and test/build entry points only. Do not use them to bypass
protected Firebase or signing custody.
