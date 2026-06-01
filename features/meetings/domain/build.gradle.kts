plugins {
    id("meet.jvm.library")
}

dependencies {
    implementation(project(":core:common"))
    api(project(":core:domain"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.paging.common)
}
