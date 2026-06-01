import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension<*, *, *, *, *, *>,
) {
    commonExtension.apply {
        compileSdk = 36
        defaultConfig {
            minSdk = 30
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }

        lint {
            // Детектор краши на Kotlin 2.0.21 Analysis API (IncompatibleClassChangeError,
            // KaSimpleVariableAccessCall). Баг в lint-тулинге, не в нашем коде.
            // Снять после бампа Kotlin/AGP в Сессии 11.
            disable += "FrequentlyChangingValue"
            disable += "RememberInComposition"
        }
    }
    configureKotlin()


}

internal fun Project.configureKotlin() {
    tasks.withType(KotlinCompile::class.java).configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }


}

