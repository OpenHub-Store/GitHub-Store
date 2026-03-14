plugins {
    alias(libs.plugins.convention.kmp.library)
    alias(libs.plugins.convention.room)
    alias(libs.plugins.convention.buildkonfig)
}

android {
    buildFeatures {
        aidl = true
    }
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)

                implementation(projects.core.domain)

                implementation(libs.bundles.ktor.common)
                implementation(libs.bundles.koin.common)

                implementation(libs.touchlab.kermit)

                implementation(libs.datastore)
                implementation(libs.datastore.preferences)

                implementation(libs.kotlinx.datetime)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.ktor.client.okhttp)
                implementation(libs.androidx.work.runtime)
                implementation(libs.shizuku.api)
                implementation(libs.shizuku.provider)
                compileOnly(libs.hidden.api.stub)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }
    }
}
