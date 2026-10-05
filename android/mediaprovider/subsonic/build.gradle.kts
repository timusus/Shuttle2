// The Subsonic / OpenSubsonic (Navidrome) media provider (#502). Multiplatform like the other server providers: the HTTP
// service, sign-in, sync, stream urls, playback reporting, favourites, artwork urls and their DI are common. The
// OkHttp-backed client, the artwork auth interceptor and the MediaInfoProvider (whose MediaInfo carries an
// android.net.Uri) stay in androidMain.
plugins {
    id("s2.kmp-library")
    alias(libs.plugins.metro)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.simplecityapps.provider.subsonic"
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
            implementation(project(":android:mediaprovider:core"))
            implementation(project(":android:core"))
            implementation(project(":android:domain"))
            implementation(libs.kotlinx.datetime)
        }

        androidMain.dependencies {
            implementation(libs.androidx.core)
            implementation(libs.okhttp3.okhttp)
        }

        commonTest.dependencies {
            implementation(project(":android:mediaprovider:server-testing"))
            implementation(libs.kotlin.test)
            implementation(libs.kotest)
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}
