// The Jellyfin media provider. Multiplatform for the iOS port (#585): the HTTP services, sign-in, sync, playback
// reporting, artwork urls and their DI are common. The OkHttp-backed client, the credential store's debug-build
// sign-in and the MediaInfoProvider (whose MediaInfo carries an android.net.Uri) stay in androidMain.
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
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":android:networking"))
            implementation(project(":android:mediaprovider:server"))
            implementation(project(":android:mediaprovider:core"))
            implementation(project(":android:core"))
            implementation(project(":android:domain"))
            implementation(libs.kotlinx.datetime)
        }

        androidMain.dependencies {
            implementation(libs.androidx.core)
        }

        commonTest.dependencies {
            implementation(project(":android:mediaprovider:server-testing"))
            implementation(libs.kotlin.test)
            implementation(libs.kotest)
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}
