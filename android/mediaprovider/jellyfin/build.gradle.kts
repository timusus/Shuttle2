// The Jellyfin media provider. Multiplatform for the iOS port (#585): the HTTP services and their DTOs are common;
// sign-in, sync, playback reporting and DI stay in androidMain until :android:core and :android:mediaprovider:core
// are multiplatform too.
plugins {
    id("s2.kmp-library")
    alias(libs.plugins.metro)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.simplecityapps.provider.jellyfin"
        // :android:core and :android:mediaprovider:core compile against 37, which their consumers must match
        compileSdk = 37
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":android:networking"))
            implementation(project(":android:mediaprovider:server"))
        }

        androidMain.dependencies {
            implementation(project(":android:mediaprovider:core"))
            implementation(project(":android:core"))
            implementation(project(":android:domain"))
            implementation(libs.androidx.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.timber)
        }

        getByName("androidHostTest").dependencies {
            implementation(project(":android:mediaprovider:server-testing"))
            implementation(libs.junit)
            implementation(libs.kotest)
            implementation(libs.robolectric)
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}
