plugins {
    id("meet.android.library")
    id("meet.android.serialization")
}

val releaseBuildRequested = gradle.startParameter.taskNames.any { requestedTask ->
    val taskName = requestedTask.substringAfterLast(':')
    taskName.contains("release", ignoreCase = true) ||
        taskName in setOf("assemble", "build", "bundle", "check", "package", "publish", "test")
}

fun requiredReleaseBaseUrl(): String {
    val baseUrl = providers.gradleProperty("BASE_URL_RELEASE").orNull
    check(baseUrl == "https://api.whysoezzy.online") {
        "BASE_URL_RELEASE is required for release builds. " +
            "Set it exactly to https://api.whysoezzy.online."
    }

    return requireNotNull(baseUrl)
}

android {
    namespace = "com.whysoezzy.network"

    buildTypes {
        debug {
            val baseUrl = (project.findProperty("BASE_URL_DEBUG") as? String) ?: "http://10.0.2.2:8080"
            buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
        }
        release {
            val baseUrl = if (releaseBuildRequested) requiredReleaseBaseUrl() else ""
            buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:common"))
    api(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.auth)
    api(libs.kotlinx.serialization.json)

    implementation(libs.timber)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
