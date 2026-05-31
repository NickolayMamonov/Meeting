import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

internal fun Project.configureAndroidCompose(
    commonExtension: CommonExtension<*, *, *, *, *, *>,
) {
    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

    commonExtension.buildFeatures.compose = true

    extensions.configure<ComposeCompilerGradlePluginExtension> {
        stabilityConfigurationFile.set(
            rootProject.layout.projectDirectory.file("compose-stability.conf"),
        )
    }

    dependencies {
        val bom = libs.findLibrary("androidx-compose-bom").get()
        add("implementation", platform(bom))
        add("implementation", libs.findLibrary("androidx-ui").get())
        add("implementation", libs.findLibrary("androidx-ui-graphics").get())
        add("implementation", libs.findLibrary("androidx-ui-tooling-preview").get())
        add("implementation", libs.findLibrary("androidx-material3").get())
        add("debugImplementation", libs.findLibrary("androidx-ui-tooling").get())
        add("debugImplementation", libs.findLibrary("androidx-ui-test-manifest").get())
        add("androidTestImplementation", platform(bom))
        add("androidTestImplementation", libs.findLibrary("androidx-ui-test-junit4").get())
    }
}