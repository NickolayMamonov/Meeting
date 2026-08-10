plugins {
    id("meet.android.library")
    id("meet.android.serialization")
}

android {
    namespace = "com.whysoezzy.meetings.data"
}

dependencies {
    api(project(":features:meetings:domain"))
    api(project(":core:network"))
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.paging.runtime)

    testImplementation(project(":core:testing"))
    testImplementation(libs.ktor.client.mock)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
