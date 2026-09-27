// What every media-server provider (Jellyfin, Emby, Plex) shares: paging, the sign-in skeleton a sync runs in,
// credential storage and the formats the player direct-plays (#347). DTOs, URL building and DI stay per provider.
// Multiplatform for the iOS port (#585): all of it is common except the Android string resources behind
// ServerStrings (ResourceServerStrings) and the debuggable-build check, which read a Context.
plugins {
    id("s2.kmp-library")
}

kotlin {
    android {
        namespace = "com.simplecityapps.mediaprovider.server"
        // :android:core, :android:mediaprovider:core and :android:networking compile against 37, which their consumers must match
        compileSdk = 37
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":android:networking"))
            implementation(libs.kotlinx.coroutinesCore)
            implementation(project(":android:core"))
            implementation(project(":android:mediaprovider:core"))
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotest)
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}
