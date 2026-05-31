plugins {
    id("meet.jvm.library")
}

dependencies {
    testImplementation(libs.junit)
    implementation(libs.kotlinx.coroutines.core)
}
