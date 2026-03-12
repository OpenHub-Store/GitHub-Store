import com.codingfeline.buildkonfig.compiler.FieldSpec
import com.codingfeline.buildkonfig.gradle.BuildKonfigExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import zed.rainxch.githubstore.convention.libs
import zed.rainxch.githubstore.convention.pathToPackageName
import java.util.Properties

class BuildKonfigConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.codingfeline.buildkonfig")
            }

            extensions.configure<BuildKonfigExtension> {
                packageName = target.pathToPackageName()

                defaultConfigs {
                    val localProps =
                        Properties().apply {
                            val file = rootProject.file("local.properties")
                            if (file.exists()) file.inputStream().use { this.load(it) }
                        }

                    val githubClientId =
                        (
                            localProps.getProperty("GITHUB_CLIENT_ID")
                                ?: "Ov23linTY28VFpFjFiI9"
                        ).trim()

                    val versionName = libs.findVersion("projectVersionName").get().toString()

                    buildConfigField(FieldSpec.Type.STRING, "GITHUB_CLIENT_ID", githubClientId)
                    buildConfigField(FieldSpec.Type.STRING, "VERSION_NAME", versionName)
                }
            }
        }
    }
}
