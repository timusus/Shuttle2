import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Domain layer (#443, docs/architecture/layering.md): plain Kotlin models, queries, sort orders, repository and
// playback operations interfaces, and the shared use cases, no Android.
plugins {
    // No version: the Kotlin Gradle plugin is already on the root classpath (via the Compose compiler plugin).
    `java-library`
    id("org.jetbrains.kotlin.jvm")
    // Dagger (not Hilt) generates the use cases' `@Inject` factories here, where the classes live.
    id("com.google.devtools.ksp")
    // Smart playlist rules are stored as JSON; the codec lives beside the rule model (#506).
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutinesCore)
    implementation(libs.dagger)
    ksp(libs.dagger.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotest)
}

// `support/scripts/unit-test` and CI run `testDebugUnitTest` across the project; this JVM module has no
// variants, so register a twin of `test` under that name to put its tests in the same sweep (and accept
// `--tests` filters) without touching either. Mirrors `:android:architecture-tests`.
tasks.register<Test>("testDebugUnitTest") {
    group = "verification"
    description = "Runs this module's tests (same as :test, named for the project-wide unit test sweep)."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
}
