import org.gradle.kotlin.dsl.compileOnly
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    alias(libs.plugins.flatpak.gradle.generator)
}

group = "zed.rainxch.convention.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.android.tools.common)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.androidx.room.gradle.plugin)

    implementation(libs.buildkonfig.gradlePlugin)
    implementation(libs.buildkonfig.compiler)

    implementation(libs.ktlint.gradlePlugin)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

tasks.flatpakGradleGenerator {
    outputFile = file("../../packaging/flatpak/flatpak-sources-convention.json")
    downloadDirectory.set("./offline-repository")
    excludeConfigurations.set(listOf("testCompileClasspath", "testRuntimeClasspath"))
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "zed.rainxch.convention.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidComposeApplication") {
            id = "zed.rainxch.convention.android.application.compose"
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("cmpApplication") {
            id = "zed.rainxch.convention.cmp.application"
            implementationClass = "CmpApplicationConventionPlugin"
        }
        register("kmpLibrary") {
            id = "zed.rainxch.convention.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("cmpLibrary") {
            id = "zed.rainxch.convention.cmp.library"
            implementationClass = "CmpLibraryConventionPlugin"
        }
        register("cmpFeature") {
            id = "zed.rainxch.convention.cmp.feature"
            implementationClass = "CmpFeatureConventionPlugin"
        }
        register("buildKonfig") {
            id = "zed.rainxch.convention.buildkonfig"
            implementationClass = "BuildKonfigConventionPlugin"
        }
        register("room") {
            id = "zed.rainxch.convention.room"
            implementationClass = "RoomConventionPlugin"
        }
        register("ktlint") {
            id = "zed.rainxch.convention.ktlint"
            implementationClass = "KtlintConventionPlugin"
        }
    }
}
