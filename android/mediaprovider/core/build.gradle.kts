// What every media provider shares: the MediaProvider API, MediaImporter, m3u reading and writing, library search and
// the streaming settings. Multiplatform for the iOS port (#584, docs/architecture/ios-port/phase-2-data.md): all of
// that is commonMain; the Android wiring (the WorkManager import worker, SAF playlist export, the Uri-typed
// MediaInfoProvider, the provider titles and icons from resources, network metering, the client identity's
// PackageManager and Build lookups) is androidMain.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("s2.kmp-library")
    alias(libs.plugins.metro)
}

kotlin {
    android {
        namespace = "com.simplecityapps.mediaprovider"
        // As :android:core, which it depends on
        compileSdk = 37

        // The provider titles, descriptions and icons, and the import progress messages (com.simplecityapps.mediaprovider.R)
        androidResources {
            enable = true
        }

        // As before the conversion, for the Android modules that depend on it
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.concurrent.atomics.ExperimentalAtomicApi")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }

    sourceSets {
        commonMain.dependencies {
            // api: its MediaProvider API speaks the domain's types (Song, Progress, SongImportState)
            api(project(":android:domain"))
            implementation(project(":android:core"))
            implementation(libs.kotlinx.datetime)
            api(libs.kotlinx.coroutinesCore)
            api(libs.androidx.annotation)
        }

        androidMain.dependencies {
            implementation(libs.timber)
            implementation(libs.androidx.work.runtime.ktx)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotest)
            implementation(libs.kotlinx.coroutinesTest)
        }

        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
        }
    }
}
