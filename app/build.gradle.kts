import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
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
        // Android 11+: system trash for photos (MediaStore.createTrashRequest),
        // typed foreground services and scoped storage. The SDK itself needs 24+.
        minSdk = 30
        targetSdk = 35
        versionCode = 7
        versionName = "3.4"

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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("roborazzi.test.record", "true")
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                // AskLiveTest runs the real "ask Burrow" model when given one:
                // ./gradlew testDebugUnitTest --tests '*AskLiveTest*' -Pask.model=/path/gemma-4-E2B-it.litertlm -Pask.lib=/dir/with/linux/liblitertlm_jni.so
                providers.gradleProperty("ask.model").orNull?.let { m -> it.systemProperty("ask.model", m) }
                providers.gradleProperty("ask.lib").orNull?.let { l -> it.systemProperty("java.library.path", l) }
            }
        }
    }

    packaging {
        // Each model's native library statically links its own ~50 MB Swift
        // runtime, so ten models are ~560 MB uncompressed. Compressing them in the
        // APK makes it a fraction of that to download and sideload; Android
        // extracts them once at install time.
        jniLibs.useLegacyPackaging = true
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

    // Hindi speech: AI4Bharat's IndicConformer, run with ONNX Runtime (models downloaded on request).
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.31.0")

    // "ask Burrow": Gemma 4 E2B on the phone with LiteRT-LM, which does the tool calling
    // (model downloaded on request). 0.16.1 is the newest release that builds with Kotlin 2.2.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.16.1")

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
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    val room = "2.7.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    // Screenshot tests: the real Compose screens rendered on the JVM (Robolectric), saved as PNGs by Roborazzi.
    testImplementation(composeBom)
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.32.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.32.2")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.work:work-testing:2.10.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
