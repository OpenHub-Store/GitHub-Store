plugins {
    alias(libs.plugins.convention.cmp.feature)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.datetime)

                implementation(projects.core.domain)
                implementation(projects.core.presentation)
                implementation(projects.feature.details.domain)

                implementation(libs.markdown.renderer)
                implementation(libs.markdown.renderer.coil3)
                implementation(libs.highlights)

                implementation(libs.ktor.client.core)

                implementation(libs.bundles.landscapist)

                implementation(libs.jetbrains.compose.components.resources)
                implementation(libs.androidx.compose.ui.tooling.preview)
            }
        }
    }
}
