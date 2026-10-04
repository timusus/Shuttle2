// The local library: the Room database every provider's songs and playlists are stored in, the repositories over it,
// and the MediaStore/TagLib providers. Multiplatform for the iOS port (#584, docs/architecture/ios-port/phase-2-data.md):
// the database and the repositories live in commonMain; the MediaStore/TagLib providers and the SAF m3u file sync
// (SafPlaylistFileSync, behind PlaylistFileSync) are androidMain.
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension

plugins {
    id("s2.kmp-library")
    // Android lint on the main sources (NewApi, Range, ...); the KMP Android plugin registers no lint
    // tasks of its own (#804).
    id("com.android.lint")
    alias(libs.plugins.ksp)
    alias(libs.plugins.metro)
    alias(libs.plugins.androidx.room)
}

kotlin {
    android {
        namespace = "com.simplecityapps.localmediaprovider"
        // As the Android modules it depends on (:android:core, :android:saf, the androidx libraries they pull in)
        compileSdk = 37
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    // A framework only to prove the KSP-generated Room code links (linkDebugFrameworkIosSimulatorArm64), until
    // the umbrella Shared.framework of phase 5 links this module.
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "LocalMediaProvider"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":android:core"))
            implementation(project(":android:domain"))
            implementation(project(":android:mediaprovider:core"))
            api(libs.androidx.room.runtime)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.datetime)
        }

        androidMain.dependencies {
            implementation(libs.androidx.core)
            implementation(project(":android:saf"))
            implementation(libs.timusus.ktaglib)
            api(libs.androidx.room.ktx)
        }

        iosMain.dependencies {
            // iOS has no framework SQLite; Android keeps the platform one (DatabaseProvider.android.kt).
            implementation(libs.androidx.sqlite.bundled)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotest)
        }

        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.robolectric)
            implementation(libs.androidx.junit)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.room.testing)
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
