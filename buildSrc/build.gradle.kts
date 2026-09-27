repositories {
    google()
    mavenCentral()
}

plugins {
    `kotlin-dsl`
}

dependencies {
    // The convention plugins (src/main/kotlin/*.gradle.kts) apply AGP and the Kotlin Gradle plugin, so both
    // come onto buildSrc's classpath, which every build script inherits: apply them by id, without a version.
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)

    testImplementation(libs.junit)
}

// Gradle builds only buildSrc's jar before configuring the main build, so run the tests after it (the test
// classpath needs the jar, so not before): a broken rule engine (ModuleLayers) fails every build instead of
// passing silently. Up to date unless buildSrc changes.
tasks.named("jar") {
    finalizedBy(tasks.named("test"))
}
