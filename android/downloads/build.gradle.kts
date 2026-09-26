import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("dagger.hilt.android.plugin")
    id("com.google.devtools.ksp")
}

android {
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    namespace = "com.simplecityapps.shuttle.downloads"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    // Shuttle Core
    implementation(project(":android:core"))
    implementation(project(":android:domain"))
    implementation(project(":android:mediaprovider:core"))

    // Media3: DownloadManager, DownloadService and the SimpleCache index database
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.database)

    // Hilt
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotest)
    testImplementation(libs.kotlinx.coroutinesTest)
    testImplementation(libs.kotlinx.datetime)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
}
