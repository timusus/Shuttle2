plugins {
    id("com.android.library")
    id("kotlin-parcelize")
    id("kotlin-android")
}

android {
    compileSdk = 36

    defaultConfig {
        minSdk = 23
    }
    namespace = "com.simplecityapps.shuttle.data"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    implementation(libs.kotlinx.datetime)
}
