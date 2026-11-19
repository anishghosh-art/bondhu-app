import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---------------------------------------------------------------------------------------------
// Signing keys come from environment variables (set by GitHub Actions from GitHub Secrets).
// The keystore file itself is NEVER in the repo. If any variable is missing (for example on a
// developer laptop), we simply use Android's normal debug key, so the build never fails.
// ---------------------------------------------------------------------------------------------
val keystorePath: String = System.getenv("BONDHU_KEYSTORE_PATH") ?: ""
val keystorePassword: String = System.getenv("BONDHU_KEYSTORE_PASSWORD") ?: ""
val keyAliasName: String = System.getenv("BONDHU_KEY_ALIAS") ?: ""
val keyPasswordValue: String = System.getenv("BONDHU_KEY_PASSWORD") ?: ""
val hasPermanentKey: Boolean =
    keystorePath.isNotBlank() && File(keystorePath).exists() &&
        keystorePassword.isNotBlank() && keyAliasName.isNotBlank() && keyPasswordValue.isNotBlank()

android {
    namespace = "in.bynq.bondhu.bench"
    compileSdk = 35

    defaultConfig {
        applicationId = "in.bynq.bondhu.bench"
        minSdk = 29
        targetSdk = 35
        // versionCode must grow with every build, or Android refuses to update the app.
        // CI sets GITHUB_RUN_NUMBER automatically; on a laptop it is 1.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.2.0"
    }

    signingConfigs {
        if (hasPermanentKey) {
            create("bondhu") {
                storeFile = File(keystorePath)
                storePassword = keystorePassword
                keyAlias = keyAliasName
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        debug {
            // Same permanent key for debug AND release, so in-app updates always match.
            if (hasPermanentKey) signingConfig = signingConfigs.getByName("bondhu")
        }
        release {
            // No code shrinking for now (keeps things simple for a beginner).
            isMinifyEnabled = false
            if (hasPermanentKey) signingConfig = signingConfigs.getByName("bondhu")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true // gives us BuildConfig.VERSION_CODE / VERSION_NAME for the updater
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // The LiteRT-LM library is built with a very new Kotlin. This flag stops the compiler
        // from refusing to read it just because of a version-number difference.
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

dependencies {
    // Compose BOM keeps all Compose library versions in sync.
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // On-device language model runtime (LiteRT-LM). Pinned to 0.16.1 on purpose:
    //  - it exists on Google Maven (checked 2026-11-19),
    //  - the Qwen3 model card reports that 0.18.0 and newer break the "thinking" model file.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.16.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
