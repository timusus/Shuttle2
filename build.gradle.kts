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

subprojects {
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
