plugins {
    id("com.android.application")
    id("com.github.triplet.play") version "3.3.0"
    id("kotlin-android")
    id("androidx.navigation.safeargs.kotlin")
    id("kotlin-kapt")
    id("com.bugsnag.android.gradle")
    id("com.mikepenz.aboutlibraries.plugin")
    id("kotlin-parcelize")
}

android {

    compileSdkVersion(30)

    defaultConfig {
        applicationId = "com.simplecityapps.shuttle"
        minSdkVersion(21)
        targetSdkVersion(30)
        versionName = computeVersionName()
        versionCode = computeVersionCode()
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore.ks")
            keyAlias = if (project.hasProperty("keyAlias")) project.properties["keyAlias"] as String else "default"
            storePassword = if (project.hasProperty("storePass")) project.properties["storePass"] as String else "default"
            keyPassword = if (project.hasProperty("keyPass")) project.properties["keyPass"] as String else "default"
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".dev"
        }
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    flavorDimensions("all")

    packagingOptions {
        exclude("META-INF/core_debug.kotlin_module")
        exclude("META-INF/core_release.kotlin_module")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.RequiresOptIn"
    }

    dependencies {
        implementation(fileTree("dir" to "libs", "include" to listOf("*.jar")))

        // Shuttle Core
        implementation(project(":core"))

        // Shuttle Networking
        implementation(project(":networking"))

        // TagLib
        implementation(project(":ktaglib:lib"))

        // Shuttle MediaProvider Core
        implementation(project(":mediaprovider:core"))
        implementation(project(":mediaprovider:local"))
        implementation(project(":mediaprovider:emby"))
        implementation(project(":mediaprovider:jellyfin"))
        implementation(project(":mediaprovider:plex"))

        // Shuttle Image Loader
        implementation(project(":imageloader"))

        // Shuttle Playback
        implementation(project(":playback"))

        // RecylerView Adapter
        implementation(project(":recyclerview-adapter"))

        // Storage Access Framework Helper
        implementation(project(":saf"))

        // RecyclerView FastScroll
        implementation("com.github.timusus:RecyclerView-FastScroll:dev-SNAPSHOT")

        // AppCompat
        implementation("androidx.appcompat:appcompat:1.2.0")

        // Constraint Layout
        implementation("androidx.constraintlayout:constraintlayout:2.0.4")

        // Android Arch
        implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")

        // AndroidX Navigation
        implementation("androidx.navigation:navigation-fragment-ktx:2.3.5")
        implementation("androidx.navigation:navigation-ui-ktx:2.3.5")

        // Dagger core
        kapt("com.google.dagger:dagger-compiler:2.33")

        // Dagger Android
        implementation("com.google.dagger:dagger-android-support:2.30.1")
        kapt("com.google.dagger:dagger-android-processor:2.30.1")

        // AssistedInject
        compileOnly("com.squareup.inject:assisted-inject-annotations-dagger2:0.6.0")
        kapt("com.squareup.inject:assisted-inject-processor-dagger2:0.6.0")

        // Leak Canary
        debugImplementation("com.squareup.leakcanary:leakcanary-android:2.6")

        // ViewPager 2
        implementation("androidx.viewpager2:viewpager2:1.0.0")

        // ViewPager Circle Indicator
        implementation("me.relex:circleindicator:2.1.4")

        // AndroidX Media
        implementation("androidx.media:media:1.3.0")

        // AndroidX Preference
        implementation("androidx.preference:preference-ktx:1.1.1")

        // ChromeCast
        implementation("com.google.android.gms:play-services-cast-framework:19.0.0")

        // NanoHttp
        implementation("org.nanohttpd:nanohttpd-webserver:2.3.1")

        // Moshi
        kapt("com.squareup.moshi:moshi-kotlin-codegen:1.11.0")

        // AndroidX Lifecycle
        implementation("androidx.lifecycle:lifecycle-common-java8:2.3.1")

        // Noise
        implementation("com.github.paramsen:noise:2.0.0")

        // MpAndroidChart
        implementation("com.github.PhilJay:MPAndroidChart:3.1.0")

        // BugSnag
        implementation("com.bugsnag:bugsnag-android:5.4.0")

        // AndroidX Lifecycle
        implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.3.1")

        // ExoPlayer
        implementation(project(":exoplayer-library-core"))
        implementation(project(":exoplayer-library-hls"))

        implementation("androidx.drawerlayout:drawerlayout:1.1.1")

        // New fragment manager
        implementation("androidx.fragment:fragment:1.3.2")

        // Coil
        implementation("io.coil-kt:coil-base:1.1.1")

        // About Libraries
        implementation("com.mikepenz:aboutlibraries-core:8.8.4")
    }
}

play {
    if (System.getenv("JENKINS_URL") != null) {
        serviceAccountCredentials.set(file(System.getenv("DEPLOYMENT_KEYS")))
        defaultToAppBundles.set(true)
        track.set("alpha")
    } else {
        serviceAccountCredentials.set(file("../deployment_keys.json"))
    }
}

apply(plugin = "com.google.gms.google-services")

fun computeVersionName(): String {
    // Basic <major>.<minor> version name
    if (System.getenv("JENKINS_URL") != null) {
        return String.format("%d.%d.%d", AppVersion.versionMajor, AppVersion.versionMinor, AppVersion.versionPatch)
    }
    return String.format("%d.%d.%d", AppVersion.versionMajor, AppVersion.versionMinor, AppVersion.versionPatch)
}

fun computeVersionCode(): Int {
    // Major + minor + Jenkins build number (where available)
    return (AppVersion.versionMajor * 10000000) + (AppVersion.versionMinor * 1000000) + (AppVersion.versionPatch * 10000) + Integer.valueOf(System.getenv("BUILD_NUMBER") ?: "0")
}