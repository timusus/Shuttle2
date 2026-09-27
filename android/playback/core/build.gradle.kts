// Playback policy shared by Android and iOS (#597, #602, docs/architecture/ios-port/phase-6-playback.md): the
// shuffle order, the queue's publish and navigation rules, the playerless queue model iOS plays from, where a
// song starts, the ReplayGain dB rule and the EQ presets/biquad coefficient maths (sample rate passed in, no
// platform rate lookups). Android's Media3 queue and EQ/ReplayGain audio processors (:android:playback) and the
// iOS player controller (:shared) both delegate to it.
plugins {
    id("s2.kmp-library")
}

kotlin {
    android {
        namespace = "com.simplecityapps.playback.core"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":android:domain"))
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
