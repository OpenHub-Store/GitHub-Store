plugins {
    alias(libs.plugins.convention.cmp.feature)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.collections.immutable)

                implementation(projects.core.domain)
                implementation(projects.core.data)
                implementation(projects.core.presentation)

                api(libs.ktor.client.core)

                implementation(libs.touchlab.kermit)
                implementation(libs.kotlinx.serialization.json)

                implementation(libs.coil3.compose)
                implementation(libs.coil3.svg)

                implementation(libs.androidx.compose.ui.tooling.preview)
                implementation(libs.jetbrains.compose.components.resources)
            }
        }
    }
}
