import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// What every media-server provider (Jellyfin, Emby, Plex) shares: paging, the sign-in skeleton a sync runs in,
// credential storage and the formats the player direct-plays (#347). DTOs, URL building and DI stay per provider.
plugins {
    id("com.android.library")
}

android {
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    namespace = "com.simplecityapps.mediaprovider.server"

    // FakeSharedPreferences and FixtureServer, shared by the provider modules' tests
    testFixtures {
        enable = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":android:core"))
    implementation(project(":android:mediaprovider:core"))
    implementation(project(":android:networking"))

    implementation(libs.timber)

    testFixturesApi(libs.okhttp3.mockwebserver)

    testImplementation(libs.junit)
    testImplementation(libs.kotest)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutinesTest)
}
