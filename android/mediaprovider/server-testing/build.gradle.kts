// Test doubles the media-server providers' tests share (#585): FixtureServer, a fake server on Ktor's MockEngine that
// answers each endpoint with a JSON fixture, and FakeSharedPreferences. A module of its own because multiplatform
// modules have no test fixtures.
plugins {
    id("s2.kmp-library")
}

kotlin {
    android {
        namespace = "com.simplecityapps.mediaprovider.server.testing"
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.ktor.client.mock)
        }
    }
}
