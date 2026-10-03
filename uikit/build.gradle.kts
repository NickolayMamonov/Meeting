plugins {
    id("meet.android.library.compose")
}

val mapboxDebugToken =
    providers
        .gradleProperty("MAPBOX_PUBLIC_TOKEN_DEBUG")
        .orElse(providers.environmentVariable("MAPBOX_PUBLIC_TOKEN_DEBUG"))
        .orNull
        .let(::safeMapboxPublicToken)
val mapboxReleaseToken =
    providers
        .gradleProperty("MAPBOX_PUBLIC_TOKEN_RELEASE")
        .orElse(providers.environmentVariable("MAPBOX_PUBLIC_TOKEN_RELEASE"))
        .orNull
        .let(::safeMapboxPublicToken)

android {
    namespace = "dev.whysoezzy.uikit"
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        getByName("debug") {
            buildConfigField("String", "MAPBOX_PUBLIC_TOKEN", "\"${mapboxDebugToken.orEmpty()}\"")
        }
        getByName("release") {
            buildConfigField("String", "MAPBOX_PUBLIC_TOKEN", "\"${mapboxReleaseToken.orEmpty()}\"")
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)
    implementation(libs.lottie)
    implementation(libs.lottie.compose)
    implementation(libs.mapbox.maps.android)
    implementation(libs.mapbox.maps.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

private fun safeMapboxPublicToken(token: String?): String? =
    token?.takeIf { it.startsWith("pk.") && it.matches(Regex("pk\\.[A-Za-z0-9._-]+")) }
