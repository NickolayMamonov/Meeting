plugins {
    id("meet.android.library.compose")
}

android {
    namespace = "dev.whysoezzy.features_meetings"
}

dependencies {
    implementation(project(":uikit"))
    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":features:meetings:domain"))

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.paging.compose)
    implementation(libs.coil)

    testImplementation(project(":core:testing"))
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
