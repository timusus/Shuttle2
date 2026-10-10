// What every media-server provider (Jellyfin, Emby, Plex) shares: paging, the sign-in skeleton a sync runs in,
// credential storage and the formats the player direct-plays (#347), plus the MediaBrowser API Jellyfin and Emby both
// speak (`mediabrowser`): its DTOs, library and sign-in services, sign-in and sync. DI stays per provider.
// Multiplatform for the iOS port (#585): all of it is common except the Android string resources behind
// ServerStrings (ResourceServerStrings) and the debuggable-build check, which read a Context.
plugins {
    id("s2.kmp-library")
    alias(libs.plugins.metro)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.simplecityapps.mediaprovider.server"
        // :android:core, :android:mediaprovider:core and :android:networking compile against 37, which their consumers must match
        compileSdk = 37
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":android:networking"))
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.datetime)
            implementation(project(":android:core"))
            implementation(project(":android:mediaprovider:core"))
        }

        commonTest.dependencies {
            implementation(project(":android:mediaprovider:server-testing"))
            implementation(libs.kotlin.test)
            implementation(libs.kotest)
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}
