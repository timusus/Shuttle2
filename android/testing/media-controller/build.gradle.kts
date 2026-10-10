import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// A bare-bones outside media controller for the emulator checks: connects to Shuttle's session as a third-party app
// would and does what an adb intent says (support/scripts/checks/external-controller-*.sh). Debug-only, never shipped,
// and nothing depends on it.
plugins {
    id("com.android.application")
}

android {
    namespace = "com.simplecityapps.shuttle.testing.controller"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.simplecityapps.shuttle.testing.controller"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

androidComponents {
    beforeVariants { variant ->
        if (variant.buildType == "release") variant.enable = false
    }
}

dependencies {
    implementation(libs.media3.session)
    // MediaBrowserCompat/MediaControllerCompat: the old-session client RS-46 and RS-61 exercise.
    implementation(libs.androidx.media)
}
