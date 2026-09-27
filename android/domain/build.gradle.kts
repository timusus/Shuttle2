// Domain layer (#443, docs/architecture/layering.md): plain Kotlin models, queries, sort orders, repository and
// playback operations interfaces, and the shared use cases, no Android. Multiplatform for the iOS port (#582).
plugins {
    id("s2.kmp-library")
    // Metro generates the use cases' `@Inject` factories here, where the classes live. Until :android:app moves
    // off Hilt (#583), app/di/DomainBridgeModule.kt provides them to Hilt by hand.
    alias(libs.plugins.metro)
    // Smart playlist rules are stored as JSON; the codec lives beside the rule model (#506).
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.simplecityapps.shuttle.domain"
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutinesCore)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotest)
        }
    }
}
