import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Domain layer (#443, docs/architecture/layering.md): plain Kotlin models, queries and sort orders, no Android.
plugins {
    // No version: the Kotlin Gradle plugin is already on the root classpath (via the Compose compiler plugin).
    id("org.jetbrains.kotlin.jvm")
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
}
