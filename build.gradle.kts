// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.ktlint) apply false
}

subprojects {
    afterEvaluate {
        extensions.findByType(com.android.build.api.dsl.CommonExtension::class.java)
            ?.lint
            ?.disable
            ?.add("NullSafeMutableLiveData")
    }

    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set(rootProject.libs.versions.ktlint.get())
        android.set(true)
        ignoreFailures.set(false)

        filter {
            exclude { entry -> entry.file.path.contains("/build/") }
            exclude { entry -> entry.file.path.contains("/generated/") }
        }

        reporters {
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.HTML)
        }
    }
}