import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Konsist architecture rules (#443). Test-only JVM module: it parses the production sources of every
// other module under android/ and checks them against baselined rules; nothing depends on it.
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
    }
}

dependencies {
    testImplementation(libs.konsist)
    testImplementation(libs.junit)
}

val baselineDir = layout.projectDirectory.dir("src/test/baselines")
val updateBaselines = providers.gradleProperty("updateArchitectureBaselines").map { it != "false" }.orElse(false)

// `support/scripts/unit-test` and CI run `testDebugUnitTest` across the project; this JVM module has no
// variants, so register a twin of `test` under that name to put the rules in the same sweep (and accept
// `--tests` filters) without touching either.
val testDebugUnitTest = tasks.register<Test>("testDebugUnitTest") {
    group = "verification"
    description = "Runs the architecture rules (same as :test, named for the project-wide unit test sweep)."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
}

tasks.withType<Test>().configureEach {
    // The rules read sources outside this module, so declare them as inputs to keep up-to-date checks honest.
    inputs.files(
        fileTree(rootDir.resolve("android")) {
            include("**/src/**/*.kt")
            exclude("**/build/**", "architecture-tests/**")
        },
    ).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("productionSources")
    inputs.dir(baselineDir).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("baselines")
    inputs.property("updateBaselines", updateBaselines)
    val reportDir = layout.buildDirectory.dir("reports/architecture/$name")
    outputs.dir(reportDir).withPropertyName("violationReport")
    systemProperty("architecture.rootDir", rootDir.absolutePath)
    systemProperty("architecture.baselineDir", baselineDir.asFile.absolutePath)
    systemProperty("architecture.reportDir", reportDir.get().asFile.absolutePath)
    systemProperty("architecture.updateBaselines", updateBaselines.get().toString())
}
