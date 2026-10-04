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

// Konsist parses and caches every Kotlin file under the project root it detects (the nearest ancestor of the
// test JVM's working dir holding `gradlew`), whatever scope a rule asks for. Run against the real checkout that
// takes in `.claude/worktrees/` copies of the whole repo and runs out of heap, so the rules run against a
// private root holding only the production sources, at their repo-relative paths.
val konsistRoot = layout.buildDirectory.dir("konsist-root")
val syncKonsistRoot = tasks.register<Sync>("syncKonsistRoot") {
    description = "Copies the production sources the rules read into a private Konsist project root."
    from(rootDir.resolve("android")) {
        include("**/src/**/*.kt")
        exclude("**/build/**", "**/architecture-tests/**", "**/.*/**")
        // No test source sets (`test`, `androidTest`, `testFixtures`, ...), as the rules only check production code.
        // Files only: a module directory such as `presentation-testing` has no `src` segment yet and must stay.
        exclude { element ->
            val segments = element.relativePath.segments
            val src = segments.indexOf("src")
            !element.isDirectory && src >= 0 && segments.getOrNull(src + 1)?.contains("test", ignoreCase = true) == true
        }
        eachFile { relativePath = RelativePath(true, "android", *relativePath.segments) }
        includeEmptyDirs = false
    }
    from(rootDir.resolve("gradlew")) // Konsist's root marker
    into(konsistRoot)
}

// Kotlin/Native rejects some characters in backtick test names (#821), so `NativeTestNameRules` reads the real
// commonTest sources, which the production-only Konsist root above leaves out.
val commonTestSources = fileTree(rootDir) {
    include("android/**/src/commonTest/**/*.kt", "shared/src/commonTest/**/*.kt")
    exclude("**/build/**", "**/.*/**")
}

tasks.withType<Test>().configureEach {
    inputs.files(commonTestSources).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("commonTestSources")
    systemProperty("architecture.repoRoot", rootDir.absolutePath)
    // Konsist parses every module's sources; the default 512 MB test heap runs out in the full sweep.
    maxHeapSize = "2g"
    // The module layer rules (root `verifyModuleLayers`, #443) gate landings alongside the Konsist rules.
    dependsOn(":verifyModuleLayers")
    dependsOn(syncKonsistRoot)
    // The rules read the synced sources, so they are inputs: up-to-date checks stay honest.
    inputs.dir(konsistRoot).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("productionSources")
    inputs.dir(baselineDir).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("baselines")
    inputs.property("updateBaselines", updateBaselines)
    val reportDir = layout.buildDirectory.dir("reports/architecture/$name")
    outputs.dir(reportDir).withPropertyName("violationReport")
    workingDir = konsistRoot.get().asFile
    systemProperty("architecture.rootDir", konsistRoot.get().asFile.absolutePath)
    systemProperty("architecture.baselineDir", baselineDir.asFile.absolutePath)
    systemProperty("architecture.reportDir", reportDir.get().asFile.absolutePath)
    systemProperty("architecture.updateBaselines", updateBaselines.get().toString())
}
