import androidx.room.gradle.RoomExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import zed.rainxch.githubstore.convention.libs

class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.google.devtools.ksp")
                apply("androidx.room")
            }

            extensions.configure<RoomExtension> {
                schemaDirectory("$projectDir/schemas")
            }

            dependencies {
                "commonMainApi"(libs.findLibrary("androidx-room-runtime").get())
                "commonMainApi"(libs.findLibrary("sqlite-bundled").get())
                "kspAndroid"(libs.findLibrary("androidx-room-compiler").get())
                "kspJvm"(libs.findLibrary("androidx-room-compiler").get())
            }
        }
    }
}
