// Kotlin Multiplatform library convention (iOS port, #582, docs/architecture/ios-port.md): Android plus the
// iOS device and simulator targets. AGP 9 doesn't allow com.android.library alongside the multiplatform
// plugin, so the Android target comes from com.android.kotlin.multiplatform.library. Each module still sets
// its own `kotlin { android { namespace = ... } }`.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

var hostTestConfig: com.android.build.api.dsl.KotlinMultiplatformAndroidHostTest? = null

kotlin {
    jvmToolchain(17)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        compileSdk = 36
        minSdk = 24
        // Runs commonTest (and androidHostTest) on the JVM, as testDebugUnitTest does for Android modules. AGP allows
        // one host test component, so it's declared here for every module. Android resources and assets are merged for
        // it only in modules that run Robolectric (below).
        withHostTest { hostTestConfig = this }
    }

    iosArm64()
    iosSimulatorArm64()
}

// Merging Android resources and assets into the host tests (what Robolectric tests read, e.g. the Room migration
// tests loading the exported schemas) costs every module's build, so it's on only where androidHostTest declares a
// Robolectric dependency. Derived, not listed, so a module that adds Robolectric gets them without anyone
// remembering to; finalizeDsl runs after the module's build script has declared its dependencies.
extensions.getByType<com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension>().finalizeDsl {
    val hostTestDependencies = configurations.getByName(kotlin.sourceSets.getByName("androidHostTest").implementationConfigurationName)
    hostTestConfig?.isIncludeAndroidResources = hostTestDependencies.dependencies.any { it.group == "org.robolectric" }
}

// `support/scripts/unit-test` and CI run `testDebugUnitTest` across the project; the multiplatform Android
// target names its JVM test task `testAndroidHostTest`. Register a twin of it under the sweep's name (a Test
// task, so it accepts `--tests` filters), as the plain JVM modules do for `test`.
val hostTest = tasks.withType<Test>().named { it == "testAndroidHostTest" }
tasks.register<Test>("testDebugUnitTest") {
    group = "verification"
    description = "Runs this module's host tests (same as :testAndroidHostTest, named for the project-wide unit test sweep)."
    testClassesDirs = files(provider { hostTest.map { it.testClassesDirs } })
    classpath = files(provider { hostTest.map { it.classpath } })
    // And the JVM settings AGP gives it: Robolectric needs its --add-opens and headless AWT.
    hostTest.forEach { androidHostTest ->
        jvmArgs(androidHostTest.jvmArgs.orEmpty().filterNot { it in jvmArgs.orEmpty() })
        systemProperties(androidHostTest.systemProperties)
    }
}

// Kotlin/Native bundles no test resources, so iOS simulator tests read commonTest's from the source tree, which the
// simulator shares with the host (FixtureServer does). simctl hands a SIMCTL_CHILD_ variable to the test process
// without the prefix. -Ps2.iosSimulatorUdid=<udid> runs them on that simulator (the landing and full verify pass
// their leased one, so the shared pool stays the only thing that boots simulators) instead of the task's own pick.
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    providers.gradleProperty("s2.iosSimulatorUdid").orNull?.let { device.set(it) }
    environment("SIMCTL_CHILD_S2_TEST_RESOURCES", layout.projectDirectory.dir("src/commonTest/resources").asFile.absolutePath)
}
