import com.android.build.api.variant.FilterConfiguration
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

    defaultConfig {
        applicationId = "dev.micr0.localmathy"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // NOTE: version codes now stride by 10 per release so per-ABI
        // offsets (+1..+4) never collide across releases.
        versionCode = 10
        versionName = "1.0.8"
    }

    // Split APKs by ABI. litertlm-android only ships native libs for
    // arm64-v8a and x86_64, so those are the only ABIs we can build.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            // Also produce a universal APK (used for GitHub releases).
            // F-Droid strips this line in prebuild and builds one ABI per
            // build block instead.
            isUniversalApk = true
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

// Per-ABI version codes, F-Droid convention: armeabi-v7a < arm64-v8a < x86 < x86_64.
// Full map kept so codes stay stable if more ABIs are ever added.
val abiCodes = mapOf(
    "armeabi-v7a" to 1,
    "arm64-v8a" to 2,
    "x86" to 3,
    "x86_64" to 4,
)

// Modern AGP variant API (androidComponents) — unlike the old
// output.versionCodeOverride, this works alongside the Kotlin Multiplatform
// plugin because it sets the version code lazily at execution time.
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters
                .find { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier
            abiCodes[abi]?.let { offset ->
                output.versionCode.set(offset + (output.versionCode.get() ?: 0))
            }
        }
    }
}