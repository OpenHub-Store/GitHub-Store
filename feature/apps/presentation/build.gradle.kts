plugins {
    alias(libs.plugins.convention.cmp.feature)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)

                implementation(projects.core.domain)
                implementation(projects.core.presentation)
                implementation(projects.feature.apps.domain)

                implementation(libs.androidx.compose.ui.tooling.preview)
                implementation(compose.components.resources)

                implementation(libs.bundles.landscapist)
                implementation(libs.liquid)

                implementation(libs.kotlinx.collections.immutable)
            }
        }

        androidMain {
            dependencies {
            }
        }

        jvmMain {
            dependencies {
            }
        }
    }
}
