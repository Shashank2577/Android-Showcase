import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Optional publishable key for Desert Ant license activation. Most apps can run
// without one; set `desertant.apiKey=pk_live_...` in local.properties (never
// committed) or enter it in the app's Settings screen.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val desertAntApiKey: String = localProperties.getProperty("desertant.apiKey")
    ?: providers.gradleProperty("desertant.apiKey").orNull
    ?: ""

val desertAntVersion = "4.2.1"

android {
    namespace = "com.showcase.desertant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.showcase.desertant"
        // The Desert Ant Android SDK requires API 24+.
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // arm64 only: every current phone (Galaxy Z Fold 7 included) is arm64-v8a,
        // and dropping x86_64 halves the native libraries each model brings.
        // Add "x86_64" back to run on a desktop emulator.
        ndk { abiFilters += listOf("arm64-v8a") }

        buildConfigField("String", "DESERT_ANT_API_KEY", "\"$desertAntApiKey\"")
        buildConfigField("String", "DESERT_ANT_SDK_VERSION", "\"$desertAntVersion\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key so `assembleRelease` produces an installable
            // APK out of the box. Replace with your own signing config to publish.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // Each model brings its own native library; keep them uncompressed so they
        // can be mapped straight from the APK.
        jniLibs.useLegacyPackaging = false
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    // Every Desert Ant model that ships for Android.
    implementation("ai.desertant:clear:$desertAntVersion")
    implementation("ai.desertant:ear:$desertAntVersion")
    implementation("ai.desertant:emo:$desertAntVersion")
    implementation("ai.desertant:eye:$desertAntVersion")
    implementation("ai.desertant:gist:$desertAntVersion")
    implementation("ai.desertant:moderator:$desertAntVersion")
    implementation("ai.desertant:redact:$desertAntVersion")
    implementation("ai.desertant:shapes:$desertAntVersion")
    implementation("ai.desertant:tongue:$desertAntVersion")
    implementation("ai.desertant:voz:$desertAntVersion")
    implementation("ai.desertant:who:$desertAntVersion")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
