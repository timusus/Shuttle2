// Test doubles the shared ViewModels' tests use (#586): fakes of the domain repositories, playback and queue
// operations and the presentation ports, the model builders (createSong, ...) and TestMediaActions. Shared by
// :android:presentation's commonTest (JVM and iOS) and :android:app's JVM tests, so a fake exists once. A module
// of its own because multiplatform modules have no test fixtures.
plugins {
    id("s2.kmp-library")
}

kotlin {
    android {
        namespace = "com.simplecityapps.presentation.testing"
        // As presentation
        compileSdk = 37
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":android:presentation"))
            api(libs.kotlinx.coroutinesCore)
            api(libs.kotlinx.datetime)
        }
    }
}
