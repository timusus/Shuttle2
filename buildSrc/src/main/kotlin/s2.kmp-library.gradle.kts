// Kotlin Multiplatform library convention (iOS port, #582, docs/architecture/ios-port.md): Android plus the
// iOS device and simulator targets. AGP 9 doesn't allow com.android.library alongside the multiplatform
// plugin, so the Android target comes from com.android.kotlin.multiplatform.library. Each module still sets
// its own `kotlin { android { namespace = ... } }`.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        compileSdk = 36
        minSdk = 24
        // Runs commonTest (and androidHostTest) on the JVM, as testDebugUnitTest does for Android modules.
        withHostTest {}
    }

    iosArm64()
    iosSimulatorArm64()
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
}
