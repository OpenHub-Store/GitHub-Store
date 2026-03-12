import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import zed.rainxch.githubstore.convention.configureAndroidCompose

class AndroidApplicationComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("zed.rainxch.convention.android.application")
                apply("org.jetbrains.kotlin.plugin.compose")
            }

            val commonExtension = extensions.getByType<ApplicationExtension>()
            configureAndroidCompose(commonExtension)
        }
    }
}
