plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.whysoezzy.meet"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.whysoezzy.meet"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

composeCompiler {
    stabilityConfigurationFile.set(
        rootProject.layout.projectDirectory.file("compose-stability.conf"),
    )
}

dependencies {

    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:auth"))

    implementation(project(":features:meetings:data"))
    implementation(project(":features:meetings:domain"))
    implementation(project(":features:meetings:presentation"))

    implementation(project(":features:communities:data"))
    implementation(project(":features:communities:domain"))
    implementation(project(":features:communities:presentation"))

    implementation(project(":features:profile:data"))
    implementation(project(":features:profile:domain"))
    implementation(project(":features:profile:presentation"))

    implementation(project(":features:auth"))

    implementation(project(":uikit"))

    implementation(libs.timber)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.lottie.compose)
    // Material Components нужен для Theme.MaterialComponents в themes.xml
    // (AlertDialog в Compose использует Android-тему Activity как fallback)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.navigation.compose)
}
