plugins {
    id("meet.android.library.compose")
}

android {
    namespace = "dev.whysoezzy.communities"
}

dependencies {
    implementation(project(":uikit"))
    implementation(project(":features:communities:domain"))
    implementation(project(":core:common"))
    implementation(project(":core:domain"))

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    implementation(libs.timber)

    implementation(libs.androidx.lifecycle.runtime.compose.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(project(":core:testing"))
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
