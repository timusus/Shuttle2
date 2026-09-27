// The iOS umbrella (#587, docs/architecture/ios-port.md): links the shared modules into the static
// `Shared.framework` the Xcode project at ios/project.yml consumes. Everything Swift sees is either declared
// here or exported below; SKIE turns the exported Flows into `Observing`/async sequences.
plugins {
    id("s2.kmp-library")
    alias(libs.plugins.skie)
}

skie {
    isEnabled = true
    features {
        // `Observing(flow) { value in ... }` in SwiftUI views.
        enableSwiftUIObservingPreview = true
    }
}

kotlin {
    android {
        namespace = "com.simplecityapps.shuttle.shared"
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach {
        it.binaries.framework {
            baseName = "Shared"
            isStatic = true
            binaryOption("bundleId", "com.simplecityapps.shuttle.shared")
            // Swift sees the domain types (Song, Album, ...) under their own names, not prefixed
            // `Android_domain...` copies. Each exported module must also be an `api` dependency.
            export(project(":android:domain"))
            // The shared ViewModels and their UI state (#586).
            export(project(":android:presentation"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":android:domain"))
            api(project(":android:presentation"))
            api(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotest)
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}
