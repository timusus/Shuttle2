// Scrobbling to Last.fm (#503, docs/architecture/scrobbling.md). Multiplatform so iOS scrobbles with the same code: the
// planner, its wiring to playback, the Last.fm client, the Room queue and the flush logic (ScrobbleFlusher) are
// commonMain; androidMain is the WorkManager worker and scheduler and the Android-built database and HTTP client. iOS
// builds its database and client in :shared and flushes through InProcessScrobbleFlushScheduler.
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension

plugins {
    id("s2.kmp-library")
    // Android lint on the main sources; the KMP Android plugin registers no lint tasks of its own (#804).
    id("com.android.lint")
    alias(libs.plugins.ksp)
    alias(libs.plugins.metro)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.simplecityapps.shuttle.scrobbling"
        // As the Android modules it depends on (:android:core, the androidx libraries they pull in)
        compileSdk = 37
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.concurrent.atomics.ExperimentalAtomicApi")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":android:core"))
            implementation(project(":android:domain"))
            implementation(project(":android:networking"))
            // AggregatePlaybackReporter and LibrarySettings: a server song counts as already reported (PlaybackScrobbling)
            implementation(project(":android:mediaprovider:core"))
            // Room: the module's own scrobbles.db queue
            api(libs.androidx.room.runtime)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.serialization.json)
        }

        androidMain.dependencies {
            api(libs.androidx.room.ktx)
            // WorkManager: the flush worker
            implementation(libs.androidx.work.runtime.ktx)
            // The app's shared OkHttpClient backs the Last.fm client
            implementation(libs.okhttp3.okhttp)
        }

        iosMain.dependencies {
            // iOS has no framework SQLite; Android keeps the platform one.
            implementation(libs.androidx.sqlite.bundled)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotest)
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.ktor.client.mock)
            // Song.date, for the test songs
            implementation(libs.kotlinx.datetime)
        }

        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.androidx.junit)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.room.testing)
            implementation(libs.androidx.work.testing)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}

// MigrationTestHelper reads the exported schemas as assets.
extensions.configure<KotlinMultiplatformAndroidComponentsExtension> {
    onVariants { variant ->
        variant.hostTests.values.forEach { hostTest ->
            hostTest.sources.assets?.addStaticSourceDirectory("schemas")
        }
    }
}
