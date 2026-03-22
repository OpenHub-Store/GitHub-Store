plugins {
    alias(libs.plugins.flatpak.gradle.generator)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.hot.reload) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}

tasks.flatpakGradleGenerator {
    outputFile = file("packaging/flatpak/flatpak-sources.json")
    downloadDirectory.set("./offline-repository")
    excludeConfigurations.set(listOf("testCompileClasspath", "testRuntimeClasspath"))
}

subprojects {
    afterEvaluate {
        tasks.configureEach {
            when (name) {
                "preBuild",
                "compileKotlinJvm",
                "compileKotlinAndroid",
                -> {
                    val ktlintFormat = tasks.findByName("ktlintFormat")
                    if (ktlintFormat != null) dependsOn(ktlintFormat)
                }
            }
        }
    }
}
