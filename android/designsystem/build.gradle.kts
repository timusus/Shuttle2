import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.roborazzi)
}

android {
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    namespace = "com.simplecityapps.shuttle.designsystem"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// The catalogue boards are recorded straight into the review pages: `recordRoborazziDebug` writes
// docs/design/catalog/<component>/*.png, and support/scripts/catalog regenerates the index.
roborazzi {
    outputDir.set(rootProject.file("docs/design/catalog"))
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    // material3 is pinned over the BOM in the version catalog (1.5.0-alpha29) for the Expressive
    // components; api so the app's screens build on the same version.
    api(libs.androidx.material3)
    api(libs.androidx.ui)
    implementation(libs.androidx.foundation)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.materialkolor)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.activity.compose)
    // The catalogue boards (src/debug) show the sample library's invented names and covers.
    debugImplementation(project(":android:fixtures"))

    testImplementation(composeBom)
    testImplementation(libs.junit)
    testImplementation(libs.kotest)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
