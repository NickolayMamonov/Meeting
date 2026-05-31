plugins {
    id("meet.android.library")
    id("meet.android.serialization")
}

android {
    namespace = "com.whysoezzy.data"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    api(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
