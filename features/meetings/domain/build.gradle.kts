plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
dependencies{
    implementation(project(":core:common"))

    implementation(libs.kotlinx.coroutines.core)
}
