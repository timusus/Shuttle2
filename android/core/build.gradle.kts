// Shared utilities, DI qualifiers, the settings framework and the key-value, secure and logging abstractions every
// module builds on. Multiplatform for the iOS port (#584, docs/architecture/ios-port/phase-2-data.md): the settings,
// preference managers, KeyValueStore/SecureStore/Logger and coroutine helpers live in commonMain; the Android wiring
// (SharedPreferences, EncryptedSharedPreferences, Timber, OkHttp, WorkManager) is androidMain.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("s2.kmp-library")
    alias(libs.plugins.metro)
}

kotlin {
    android {
        namespace = "com.simplecityapps.core"
        // As the androidx libraries it pulls in
        compileSdk = 37

        // The placeholder drawables and core strings the app's screens use (com.simplecityapps.core.R)
        androidResources {
            enable = true
        }

        // As before the conversion: the Android modules that inline core's functions (appGraph<T>()) target 11
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutinesCore)
        }

        androidMain.dependencies {
            // OkHttp Logging Interceptor
            api(libs.okhttp3.loggingInterceptor)

            // Kotlin Extensions
            api(libs.androidx.core.ktx)

            // AndroidX Annotations
            api(libs.androidx.annotation)

            // Timber
            api(libs.timber)

            // WorkManager: the Metro-backed WorkerFactory the modules contribute their workers to
            implementation(libs.androidx.work.runtime.ktx)

            // Tracing: trace() sections for startup and queue restore
            api(libs.androidx.tracing)

            api(libs.kotlinx.coroutinesAndroid)

            // OKHttp
            implementation(libs.okhttp3.okhttp)

            // Retrofit
            implementation(libs.retrofit2.converterMoshi)

            // Moshi
            implementation(libs.moshi)
            implementation(libs.moshi.kotlin)
            implementation(libs.moshi.adapters)

            // Encrypted Shared Preferences
            api(libs.androidx.security.crypto)

            // Material Design Components
            implementation(libs.google.material)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotest)
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}
