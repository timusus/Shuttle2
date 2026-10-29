// Domain layer (#443, docs/architecture/layering.md): plain Kotlin models, queries, sort orders, the library search index, repository and
// playback operations interfaces, and the shared use cases, no Android. Multiplatform for the iOS port (#582).
plugins {
    id("s2.kmp-library")
    // Metro generates the use cases' `@Inject` factories here, where the classes live, for the app graph to use.
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
        // SearchIndex's token cache is an AtomicReference
        optIn.add("kotlin.concurrent.atomics.ExperimentalAtomicApi")
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
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}

// The song-match corpus lives outside the repo (support/song-match), so its evaluation runs on demand, not in the test sweep.
tasks.register<JavaExec>("evaluateSongCopyMatcher") {
    group = "verification"
    description = "Scores SongCopyMatcher variants on the full song-match corpus."
    val hostTest = tasks.withType<Test>().named { it == "testAndroidHostTest" }
    classpath = files(provider { hostTest.map { it.classpath } })
    mainClass = "com.simplecityapps.shuttle.model.SongCopyEvaluationKt"
    providers.gradleProperty("corpus").orNull?.let { args(it) }
    maxHeapSize = "4g"
}
