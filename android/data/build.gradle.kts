plugins {
    id("com.android.library")
    id("kotlin-parcelize")
    id("kotlin-android")
}

android {
    compileSdk = 34

    defaultConfig {
        minSdk = 21
    }
    namespace = "com.simplecityapps.shuttle.data"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }

    dependencies {
        implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
    }
}
