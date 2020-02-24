const val kotlinVersion = "1.3.61"

object BuildPlugins {

    object Versions {
        // Top level
        const val androidGradlePlugin = "4.0.0-alpha09"
        const val safeArgsPlugin = "2.2.0-rc04"
        const val googleServicesPlugin = "4.3.3"
        const val fabricPlugin = "1.31.2"
        const val gradleVersionPlugin = "0.27.0"

        // Module
        const val playPublisher = "2.6.2"
    }

    // Top level
    const val androidGradlePlugin = "com.android.tools.build:gradle:${Versions.androidGradlePlugin}"
    const val kotlinGradlePlugin = "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion"
    const val safeArgsPlugin = "androidx.navigation:navigation-safe-args-gradle-plugin:${Versions.safeArgsPlugin}"
    const val googleServicesPlugin = "com.google.gms:google-services:${Versions.googleServicesPlugin}"
    const val fabricPlugin = "io.fabric.tools:gradle:${Versions.fabricPlugin}"
    const val gradleVersionPlugin = "com.github.ben-manes.versions"

    // Module
    const val androidApplication = "com.android.application"
    const val playPublisher = "com.github.triplet.play"
    const val kotlinAndroid = "kotlin-android"
    const val kotlinAndroidExtensions = "kotlin-android-extensions"
    const val safeArgs = "androidx.navigation.safeargs.kotlin"
    const val kapt = "kotlin-kapt"
    const val fabric = "io.fabric"
}

object AndroidSdk {
    const val minSdk = 21
    const val compileSdk = 28
    const val targetSdk = compileSdk
}

object AppVersion {
    const val versionMajor = 0
    const val versionMinor = 0
    const val versionPatch = 53
}