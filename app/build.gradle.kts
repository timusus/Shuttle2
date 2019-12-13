plugins {
    id(BuildPlugins.androidApplication)
    id(BuildPlugins.playPublisher) version BuildPlugins.Versions.playPublisher
    id(BuildPlugins.kotlinAndroid)
    id(BuildPlugins.kotlinAndroidExtensions)
    id(BuildPlugins.safeArgs)
    id(BuildPlugins.kapt)
    id(BuildPlugins.fabric)
}

android {

    compileSdkVersion(AndroidSdk.compileSdk)

    defaultConfig {
        applicationId = "com.simplecityapps.shuttle"
        minSdkVersion(AndroidSdk.minSdk)
        targetSdkVersion(AndroidSdk.targetSdk)
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

    dependencies {
        implementation(fileTree("dir" to "libs", "include" to listOf("*.jar")))

        // Shuttle Core
        implementation(project(":core"))

        // Shuttle MediaProvider Core
        implementation(project(":mediaprovider:core"))
        implementation(project(":mediaprovider:local"))

        // Shuttle Image Loader
        implementation(project(":imageloader"))

        // Shuttle Playback
        implementation(project(":playback"))

        // TagLib
        implementation(project(":taglib:lib"))

        // RecylerView Adapter
        implementation(project(":recyclerview-adapter"))

        // Storage Access Framework Helper
        implementation(project(":saf"))

        // Kotlin Standard Library
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.61")

        // Kotlin Extensions
        implementation("androidx.core:core-ktx:1.1.0")
        implementation("androidx.fragment:fragment-ktx:1.2.0-rc03")

        // RecyclerView FastScroll
        implementation("com.github.timusus:RecyclerView-FastScroll:dev-SNAPSHOT")

        // Material Design
        implementation("com.google.android.material:material:1.2.0-alpha02")

        // AppCompat
        implementation("androidx.appcompat:appcompat:1.1.0")

        // Constraint Layout
        implementation("androidx.constraintlayout:constraintlayout:1.1.3")

        // Android Arch
        implementation("androidx.lifecycle:lifecycle-extensions:2.1.0")

        // AndroidX Navigation
        implementation("androidx.navigation:navigation-fragment-ktx:2.2.0-rc03")
        implementation("androidx.navigation:navigation-ui-ktx:2.2.0-rc03")

        // RxJava
        implementation("io.reactivex.rxjava2:rxjava:2.2.15")
        implementation("io.reactivex.rxjava2:rxandroid:2.1.1")

        // RxKotlin
        implementation("io.reactivex.rxjava2:rxkotlin:2.4.0")

        // RxDogTag
        implementation("com.uber.rxdogtag:rxdogtag:0.3.0")

        // Dagger core
        implementation("com.google.dagger:dagger-android:2.25.2")
        kapt("com.google.dagger:dagger-compiler:2.25.2")

        // Dagger Android
        implementation("com.google.dagger:dagger-android-support:2.25.2")
        kapt("com.google.dagger:dagger-android-processor:2.25.2")

        // AssistedInject
        compileOnly("com.squareup.inject:assisted-inject-annotations-dagger2:0.5.2")
        kapt("com.squareup.inject:assisted-inject-processor-dagger2:0.5.2")

        // Timber
        implementation("com.jakewharton.timber:timber:4.7.1")

        // OKHttp
        implementation("com.squareup.okhttp3:okhttp:4.2.2")

        // Firebase
        implementation("com.google.firebase:firebase-core:17.2.1")

        // Crashlytics
        implementation("com.crashlytics.sdk.android:crashlytics:2.10.1")

        // Leak Canary
        debugImplementation("com.squareup.leakcanary:leakcanary-android:2.0")

        // ViewPager 2
        implementation("androidx.viewpager2:viewpager2:1.0.0")

        // ViewPager Circle Indicator
        implementation("me.relex:circleindicator:2.1.4")

        // AndroidX Media
        implementation("androidx.media:media:1.1.0")

        // AndroidX Preference
        implementation("androidx.preference:preference:1.1.0")
        implementation("androidx.preference:preference-ktx:1.1.0")

        // ChromeCast
        implementation("com.google.android.gms:play-services-cast-framework:18.0.0")

        // NanoHttp
        implementation("org.nanohttpd:nanohttpd-webserver:2.3.1")

        // Glide
        implementation("com.github.bumptech.glide:glide:4.10.0")
        kapt("com.github.bumptech.glide:compiler:4.10.0")
        implementation("com.github.bumptech.glide:okhttp3-integration:4.10.0")
    }
}

play {
    if (System.getenv("JENKINS_URL") != null) {
        serviceAccountCredentials = file(System.getenv("DEPLOYMENT_KEYS"))
        defaultToAppBundles = true
        track = "alpha"
    } else {
        serviceAccountCredentials = file("../deployment_keys.json")
    }
}

afterEvaluate {
    if (System.getenv("JENKINS_URL") != null) {
        tasks.named("processReleaseGoogleServices") {
            dependsOn("copyGoogleServices")
        }
    }
}

tasks.register<Copy>("copyGoogleServices") {
    if (System.getenv("JENKINS_URL") != null) {
        description = "Copies google-services.json from Jenkins secret file"
        from(System.getenv("GOOGLE_SERVICES"))
        include("google-services.json")
        into(".")
    }
}

apply(plugin = "com.google.gms.google-services")

fun computeVersionName(): String {
    // Basic <major>.<minor> version name
    if (System.getenv("JENKINS_URL") != null) {
        return String.format("%d.%d.%d-%s", AppVersion.versionMajor, AppVersion.versionMinor, AppVersion.versionPatch, System.getenv("GIT_TAG_NAME"))
    }
    return String.format("%d.%d.%d", AppVersion.versionMajor, AppVersion.versionMinor, AppVersion.versionPatch)
}

fun computeVersionCode(): Int {
    // Major + minor + Jenkins build number (where available)
    return (AppVersion.versionMajor * 10000000) + (AppVersion.versionMinor * 1000000) + (AppVersion.versionPatch * 10000) + Integer.valueOf(System.getenv("BUILD_NUMBER") ?: "0")
}