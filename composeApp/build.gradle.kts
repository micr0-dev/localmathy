import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.litertlm.android)
        }
    }
}

android {
    namespace = "dev.micr0.localmathy"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    val abiFilter = findProperty("abiFilter") as String?

    defaultConfig {
        applicationId = "dev.micr0.localmathy"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = when (abiFilter) {
            "armeabi-v7a" -> 41
            "arm64-v8a"   -> 42
            "x86"         -> 43
            "x86_64"      -> 44
            else          -> 4   // universal/local/CI builds with no filter
        }
        versionName = "1.0.3"

	if (abiFilter != null) {
            ndk {
                 abiFilters += abiFilter
            }
        }
    }

    // VERSION_NAME is surfaced to the UI via the Platform expect/actual.
    buildFeatures {
        buildConfig = true
    }

    sourceSets["main"].apply {
        manifest.srcFile("src/androidMain/AndroidManifest.xml")
        res.srcDirs("src/androidMain/res")
        assets.srcDirs("src/androidMain/assets")
    }

    // Release signing is driven by env vars / gradle properties (supplied by CI
    // secrets). When none are present the release build falls back to the debug
    // key below so the APK is still installable; F-Droid re-signs from source
    // regardless, so this never affects the F-Droid build.
    val keystoreFile = System.getenv("KEYSTORE_FILE")
        ?: (findProperty("KEYSTORE_FILE") as String?)
    signingConfigs {
        create("release") {
            if (!keystoreFile.isNullOrBlank()) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                    ?: findProperty("KEYSTORE_PASSWORD") as String?
                keyAlias = System.getenv("KEY_ALIAS")
                    ?: findProperty("KEY_ALIAS") as String?
                keyPassword = System.getenv("KEY_PASSWORD")
                    ?: findProperty("KEY_PASSWORD") as String?
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystoreFile.isNullOrBlank()) {
                signingConfigs.getByName("debug")
            } else {
                signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}
