// What every media-server provider (Jellyfin, Emby, Plex) shares: paging, the sign-in skeleton a sync runs in,
// credential storage and the formats the player direct-plays (#347). DTOs, URL building and DI stay per provider.
// Multiplatform for the iOS port (#585); the session, paging and credential store stay in androidMain until
// :android:core and :android:mediaprovider:core are multiplatform too.
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
        androidMain.dependencies {
            implementation(project(":android:networking"))
            implementation(libs.kotlinx.coroutinesCore)
            implementation(project(":android:core"))
            implementation(project(":android:mediaprovider:core"))
            implementation(libs.timber)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotest)
            implementation(libs.kotlinx.coroutinesTest)
        }

        getByName("androidHostTest").dependencies {
            implementation(project(":android:mediaprovider:server-testing"))
        }
    }
}
