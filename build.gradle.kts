plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.sentry) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.roborazzi) apply false
}

buildscript {
    dependencies {
    }
}

// #443: fail on project dependencies the layer rules forbid (ModuleLayers in buildSrc, docs/architecture/layering.md),
// ratcheted by the baseline next to the Konsist ones. Every module's `check` and the architecture tests depend on it.
val verifyModuleLayers = tasks.register<VerifyModuleLayers>("verifyModuleLayers") {
    group = "verification"
    description = "Fails on project dependencies that break the module layer rules (#443)."
    modules.set(subprojects.filter { it.buildFile.exists() }.map { it.path })
    baselineFile.set(layout.projectDirectory.file("android/architecture-tests/src/test/baselines/module-layers.txt"))
    updateBaseline.set(providers.gradleProperty("updateArchitectureBaselines").map { it != "false" }.orElse(false))
}

subprojects {
    // Collect declared production project dependencies as plain strings while each module configures, so the
    // task never touches another project's model (configuration-cache safe, no afterEvaluate ordering).
    val from = path
    configurations.configureEach {
        if (ModuleLayers.isProductionBucket(name)) {
            val configuration = name
            dependencies.withType<ProjectDependency>().configureEach {
                val entry = "$from\t$path\t$configuration"
                verifyModuleLayers.configure { declaredDependencies.add(entry) }
            }
        }
    }
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(verifyModuleLayers)
    }

    tasks.withType<Test>().configureEach {
        // Robolectric's NATIVE graphics/sqlite modes need more than the 512m default heap.
        maxHeapSize = "2g"

        // Linux renders Compose text and rounded-corner anti-aliasing a little differently than
        // the macOS-recorded docs/design Roborazzi goldens (observed diffs peak at ~0.15% of
        // pixels, #458). Tolerate it only on Linux -- the WSL box and ubuntu-latest CI both hit
        // this -- via a system property the affected tests read as s2.roborazzi.changeThreshold;
        // macOS, where the goldens are recorded, gets no override and compares exactly.
        if (System.getProperty("os.name").orEmpty().contains("Linux", ignoreCase = true)) {
            systemProperty("s2.roborazzi.changeThreshold", "0.0016")
        }

        // :android:app's 896 tests (562 Robolectric) run in one JVM by default -- the longest task
        // on the landing critical path (#542). Forks are a wall-time lever, not a CPU one: each
        // extra fork repeats Robolectric's ~6-10s sandbox start and can take up to the 2g test
        // heap, so this stays opt-in (a shared build box with other work running gets no benefit)
        // rather than a new default. Set -Ps2.testForks=2 on a quiet Mac or CI.
        if (path == ":android:app") {
            maxParallelForks = providers.gradleProperty("s2.testForks").map(String::toInt).getOrElse(1)
        }
    }

    // #402: :android:fixtures is compileOnly/debug-only; guard every module's release runtime
    // classpath against resolving it, without needing a signed release build. `matching(...).all`
    // reacts whenever AGP creates the variant configuration, regardless of afterEvaluate ordering.
    if (path != ":android:fixtures") {
        configurations.matching { it.name == "releaseRuntimeClasspath" }.all {
            val releaseRuntimeClasspath = this
            val verifyFixturesNotInReleaseClasspath = tasks.register<VerifyFixturesNotInReleaseClasspath>("verifyFixturesNotInReleaseClasspath") {
                group = "verification"
                description = "Fails if this module's release runtime classpath resolves :android:fixtures (#402)."
                modulePath.set(path)
                rootComponent.set(releaseRuntimeClasspath.incoming.resolutionResult.rootComponent)
            }
            tasks.matching { it.name == "check" }.configureEach {
                dependsOn(verifyFixturesNotInReleaseClasspath)
            }
        }
    }
}
