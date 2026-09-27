// Test doubles the media-server providers' tests share (#585): FixtureServer, a fake server on Ktor's MockEngine that
// answers each endpoint with a JSON fixture, and FakeSharedPreferences. A module of its own because multiplatform
// modules have no test fixtures.
plugins {
    id("s2.kmp-library")
}

kotlin {
    android {
        namespace = "com.simplecityapps.mediaprovider.server.testing"
        // The providers' tests still build for JVM 11 and call this module's inline Ktor API
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.ktor.client.mock)
        }

        androidMain.dependencies {
            // FixtureServer.okHttpClient(), for the providers' Retrofit services until they move to Ktor (#585 step 3)
            api(libs.okhttp3.okhttp)
            implementation(libs.kotlinx.coroutinesCore)
        }
    }
}
