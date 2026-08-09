plugins {
    id("meet.android.library")
    id("meet.android.serialization")
}

android {
    namespace = "com.whysoezzy.auth"

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    testOptions {
        unitTests.all {
            it.jvmArgs("-XX:+EnableDynamicAgentLoading")
        }
    }
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:common"))

    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.google.tink.android)
    implementation(libs.timber)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)

    testImplementation(project(":core:testing"))
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
