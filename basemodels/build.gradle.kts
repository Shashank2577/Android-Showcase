// The shared model store (protocol v1). Its sources are synced from the base
// platform (baseApp/packages/base_models/android) by `./base sync`: change them
// there, not here. Only this build file belongs to Burrow.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.baseplatform.base_models"
    compileSdk = 35

    defaultConfig {
        minSdk = 26 // java.nio.file and java.time
        // No manifestPlaceholders: a library's own values would beat the app's.
        // Burrow sets the vendor values in app/build.gradle.kts.
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    // Real org.json for JVM unit tests (android.jar only has stubs).
    testImplementation("org.json:json:20250517")
}
