repositories {
   mavenCentral()
}

plugins {
    `kotlin-dsl`
}

dependencies {
    testImplementation(libs.junit)
}

// Gradle builds only buildSrc's jar before configuring the main build, so run the tests after it (the test
// classpath needs the jar, so not before): a broken rule engine (ModuleLayers) fails every build instead of
// passing silently. Up to date unless buildSrc changes.
tasks.named("jar") {
    finalizedBy(tasks.named("test"))
}
