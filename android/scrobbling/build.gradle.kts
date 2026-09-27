import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Scrobbling to ListenBrainz and Last.fm (#503, docs/architecture/scrobbling.md). An Android library because
// this slice adds the Room queue, the flush worker and the Last.fm client; the planner itself is pure Kotlin.
plugins {
    id("com.android.library")
    id("dev.zacsweers.metro")
    id("com.google.devtools.ksp")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    namespace = "com.simplecityapps.shuttle.scrobbling"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":android:core"))
    implementation(project(":android:domain"))

    // Room: the module's own scrobbles.db queue
    ksp(libs.androidx.room.compiler)
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)

    // WorkManager: the flush worker
    implementation(libs.androidx.work.runtime.ktx)

    // Last.fm client
    implementation(libs.retrofit2.retrofit)
    implementation(libs.retrofit2.converterMoshi)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.kotlinCodegen)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotest)
    testImplementation(libs.kotlinx.datetime)
    testImplementation(libs.kotlinx.coroutinesTest)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.core.ktx)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.okhttp3.mockwebserver)
}
