import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Scrobbling to ListenBrainz and Last.fm (#503, docs/architecture/scrobbling.md). An Android library because
// later slices add the Room queue, the flush worker and the service clients; the planner itself is pure Kotlin.
plugins {
    id("com.android.library")
}

android {
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    namespace = "com.simplecityapps.shuttle.scrobbling"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":android:domain"))

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotest)
    testImplementation(libs.kotlinx.datetime)
}
