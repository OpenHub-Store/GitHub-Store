package zed.rainxch.githubstore.convention

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getting
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.util.Properties

internal fun Project.configureJvmTarget() {
    extensions.configure<KotlinMultiplatformExtension> {
        jvm()
    }
}
