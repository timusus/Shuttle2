import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The invented sample library (library.json) and its generated covers, for screenshots, tests and the
// debug design catalogue. Test and debug code only: never an implementation dependency of a release build.
plugins {
    id("com.android.library")
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

    namespace = "com.simplecityapps.shuttle.fixtures"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    api("androidx.compose.ui:ui-graphics")
    implementation(libs.moshi)

    testImplementation(libs.junit)
    testImplementation(libs.kotest)
}
