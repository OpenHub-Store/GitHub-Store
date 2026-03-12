plugins {
    alias(libs.plugins.convention.cmp.library)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)

                implementation(projects.core.domain)

                implementation(libs.bundles.landscapist)
                implementation(libs.liquid)

                implementation(libs.jetbrains.lifecycle.compose)
                implementation(libs.kotlinx.datetime)

                implementation(compose.components.resources)

                implementation(libs.androidx.compose.ui.tooling.preview)
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

compose.resources {
    publicResClass = true
    packageOfResClass = "zed.rainxch.githubstore.core.presentation.res"
    generateResClass = auto
}
