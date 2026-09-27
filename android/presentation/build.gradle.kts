// Shared presentation layer (iOS port phase 4, #586, docs/architecture/ios-port/phase-4-viewmodels.md): the
// ViewModels, their UI state and the screen use cases, in commonMain so Android's Compose screens and iOS's
// SwiftUI views drive the same ViewModels. Sees core and domain only; no Compose, no Android resources.
plugins {
    id("s2.kmp-library")
    // ViewModels contribute themselves to the app graph (`@ViewModelKey` + `@ContributesIntoMap(AppScope::class)`).
    alias(libs.plugins.metro)
    // The bundled changelog and licences metadata are JSON (ChangelogRepository, LicencesRepository).
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.simplecityapps.shuttle.presentation"
        // As core and the androidx libraries it pulls in
        compileSdk = 37
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":android:domain"))
            // The settings (ReadSetting/SaveSetting/ObserveSetting), preference managers, Logger and the app scope
            api(project(":android:core"))
            // The KMP ViewModel + viewModelScope, and metrox's ViewModel keys and factory; api because the
            // ViewModels, their keys and the factory are this module's API to both platforms' graphs.
            api(libs.androidx.lifecycle.viewmodel.kmp)
            api(libs.metrox.viewmodel)
            api(libs.kotlinx.coroutinesCore)
            // Changeset's release date
            api(libs.kotlinx.datetime)
            // The bundled changelog and licences metadata
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(project(":android:presentation-testing"))
            implementation(libs.kotlin.test)
            implementation(libs.kotest)
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}
