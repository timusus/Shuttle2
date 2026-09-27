// Playback policy shared by Android and iOS (#597, #602, docs/architecture/ios-port/phase-6-playback.md): the
// shuffle order, the queue's publish and navigation rules, the playerless queue model iOS plays from, where a
// song starts, the ReplayGain dB rule and the EQ presets/biquad coefficient maths (sample rate passed in, no
// platform rate lookups), and PlaybackSettings, which Android and the iOS settings catalog both read. Android's Media3 queue and EQ/ReplayGain audio processors (:android:playback) and the
// iOS player controller (:shared) both delegate to it.
plugins {
    id("s2.kmp-library")
    // PlaybackSettings is an @Inject singleton
    alias(libs.plugins.metro)
}

kotlin {
    android {
        namespace = "com.simplecityapps.playback.core"
        // As :android:core, which it depends on
        compileSdk = 37
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":android:domain"))
            // PlaybackSettings: Setting and SettingsStore
            implementation(project(":android:core"))
            api(libs.kotlinx.coroutinesCore)
        }

        commonTest.dependencies {
            // Song's dates are kotlinx.datetime types, which :android:domain keeps to itself.
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlin.test)
            implementation(libs.kotest)
        }
    }
}
