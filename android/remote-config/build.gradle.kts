import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")

}

android {
    compileSdk = 36

    defaultConfig {
        minSdk = 23

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    namespace = "com.simplecityapps.shuttle.remote_config"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)

    // Firebase
    api(platform(libs.firebase.bom))
    api(libs.firebase.config.ktx)
    implementation(libs.firebase.analytics.ktx)
    api(libs.kotlinx.coroutinesPlayServices)

    // Hilt
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    // S2 Core
    implementation(project(":android:core"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
