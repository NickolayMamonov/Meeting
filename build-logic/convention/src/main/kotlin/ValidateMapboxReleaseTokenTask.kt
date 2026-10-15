import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

abstract class ValidateMapboxReleaseTokenTask : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val token: Property<String>

    @TaskAction
    fun validate() {
        MapboxPublicToken.validateReleaseValue(token.orNull)
    }
}
