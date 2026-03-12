package zed.rainxch.githubstore.convention

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

fun Project.configureAndroidCompose(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    with(commonExtension) {
        buildFeatures {
            compose = true
        }

        dependencies {
            val composeBom = libs.findLibrary("androidx-compose-bom").get()

            "implementation"(platform(composeBom))
            "testImplementation"(platform(composeBom))
            "debugImplementation"(libs.findLibrary("androidx-compose-ui-tooling-preview").get())
            "debugImplementation"(libs.findLibrary("androidx-compose-ui-tooling").get())
        }
    }
}
