import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import zed.rainxch.githubstore.convention.configureKotlinAndroid
import zed.rainxch.githubstore.convention.libs

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
            }

            extensions.configure<ApplicationExtension> {
                namespace = "zed.rainxch.githubstore"
                compileSdk =
                    libs
                        .findVersion("projectCompileSdkVersion")
                        .get()
                        .toString()
                        .toInt()

                defaultConfig {
                    applicationId = libs.findVersion("projectApplicationId").get().toString()
                    minSdk =
                        libs
                            .findVersion("projectMinSdkVersion")
                            .get()
                            .toString()
                            .toInt()
                    targetSdk =
                        libs
                            .findVersion("projectTargetSdkVersion")
                            .get()
                            .toString()
                            .toInt()
                    versionCode =
                        libs
                            .findVersion("projectVersionCode")
                            .get()
                            .toString()
                            .toInt()
                    versionName = libs.findVersion("projectVersionName").get().toString()
                }
                packaging {
                    resources {
                        excludes += "/META-INF/{AL2.0,LGPL2.1}"
                    }
                }
                signingConfigs {
                    create("release") {
                        val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
                        if (keystorePath != null) {
                            storeFile = file(keystorePath)
                            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: ""
                            keyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: ""
                            keyPassword = System.getenv("ANDROID_KEY_PASSWORD") ?: ""
                            storeType = "PKCS12"
                        }
                    }
                }

                buildTypes {
                    getByName("release") {
                        isMinifyEnabled = true
                        isShrinkResources = true
                        signingConfig = signingConfigs.findByName("release")

                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                    }
                }

                configureKotlinAndroid(this)
            }
        }
    }
}
