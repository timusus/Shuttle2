// Shared presentation layer (iOS port phase 4, #586, docs/architecture/ios-port/phase-4-viewmodels.md): the
// ViewModels, their UI state and the screen use cases, in commonMain so Android's Compose screens and iOS's
// SwiftUI views drive the same ViewModels. Sees domain only; no Compose, no Android resources.
plugins {
    id("s2.kmp-library")
    // ViewModels contribute themselves to the app graph (`@ViewModelKey` + `@ContributesIntoMap(AppScope::class)`).
    alias(libs.plugins.metro)
}

kotlin {
    android {
        namespace = "com.simplecityapps.shuttle.presentation"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":android:domain"))
            // The KMP ViewModel + viewModelScope, and metrox's ViewModel keys and factory; api because the
            // ViewModels, their keys and the factory are this module's API to both platforms' graphs.
            api(libs.androidx.lifecycle.viewmodel.kmp)
            api(libs.metrox.viewmodel)
            api(libs.kotlinx.coroutinesCore)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotest)
        }
    }
}
