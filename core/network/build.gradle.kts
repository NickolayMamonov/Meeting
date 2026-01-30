plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.whysoezzy.network"
    compileSdk = 35

    defaultConfig {
        minSdk = 30

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080\"")
        }
        release {
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(libs.ktor.client.core)
    api(libs.ktor.client.android)
    api(libs.ktor.client.content.negotiation)
    api(libs.ktor.serialization.json)
    api(libs.ktor.client.logging)
    api(libs.ktor.client.auth)
    implementation("io.ktor:ktor-client-okhttp:3.0.2")

    api(libs.kotlinx.serialization.json)
    implementation(project(":core:common"))

    // Desugaring for LocalDateTime
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Android
    implementation(libs.androidx.core.ktx)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}