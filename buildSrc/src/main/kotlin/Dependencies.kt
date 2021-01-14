const val kotlinVersion = "1.4.21"

object BuildPlugins {

    object Versions {
        // Top level
        const val androidGradlePlugin = "4.0.1"
        const val safeArgsPlugin = "2.3.0"
        const val googleServicesPlugin = "4.3.3"
        const val bugsnagPlugin = "5.0.1"
        const val gradleVersionPlugin = "0.27.0"

        // Module
        const val playPublisher = "3.0.0"
    }

    // Top level
    const val androidGradlePlugin = "com.android.tools.build:gradle:${Versions.androidGradlePlugin}"
    const val kotlinGradlePlugin = "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion"
    const val safeArgsPlugin = "androidx.navigation:navigation-safe-args-gradle-plugin:${Versions.safeArgsPlugin}"
    const val googleServicesPlugin = "com.google.gms:google-services:${Versions.googleServicesPlugin}"
    const val bugsnagPlugin = "com.bugsnag:bugsnag-android-gradle-plugin:${Versions.bugsnagPlugin}"
    const val gradleVersionPlugin = "com.github.ben-manes.versions"

    // Module
    const val androidApplication = "com.android.application"
    const val playPublisher = "com.github.triplet.play"
    const val kotlinAndroid = "kotlin-android"
    const val safeArgs = "androidx.navigation.safeargs.kotlin"
    const val kapt = "kotlin-kapt"
    const val bugsnag = "com.bugsnag.android.gradle"
}

object AndroidSdk {
    const val minSdk = 21
    const val compileSdk = 29
    const val targetSdk = compileSdk
}

object AppVersion {
    const val versionMajor = 0
    const val versionMinor = 2
    const val versionPatch = 5
}