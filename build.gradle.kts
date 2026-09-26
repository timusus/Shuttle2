plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
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
