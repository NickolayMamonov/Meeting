plugins {
    id("meet.jvm.library")
}

dependencies {
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    implementation(project(":core:common"))
}