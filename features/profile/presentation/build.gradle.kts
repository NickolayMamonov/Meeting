plugins {
    id("meet.android.library.compose")
}

android {
    namespace = "dev.whysoezzy.profile"
}

dependencies {
    implementation(project(":uikit"))
    implementation(project(":features:profile:domain"))
    implementation(project(":core:domain"))
    implementation(project(":core:auth"))
    implementation(project(":core:common"))

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

    testImplementation(project(":core:testing"))
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
