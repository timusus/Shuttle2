plugins {
    id(BuildPlugins.androidApplication)
    id(BuildPlugins.playPublisher) version BuildPlugins.Versions.playPublisher
    id(BuildPlugins.kotlinAndroid)
    id(BuildPlugins.kotlinAndroidExtensions)
    id(BuildPlugins.safeArgs)
    id(BuildPlugins.kapt)
    id(BuildPlugins.bugsnag)
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
        implementation("androidx.navigation:navigation-fragment-ktx:2.3.1")
        implementation("androidx.navigation:navigation-ui-ktx:2.3.1")

        // Dagger core
        kapt("com.google.dagger:dagger-compiler:2.29.1")

        // Dagger Android
        implementation("com.google.dagger:dagger-android-support:2.29.1")
        kapt("com.google.dagger:dagger-android-processor:2.29.1")

        // AssistedInject
        compileOnly("com.squareup.inject:assisted-inject-annotations-dagger2:0.6.0")
        kapt("com.squareup.inject:assisted-inject-processor-dagger2:0.6.0")

        // Leak Canary
        debugImplementation("com.squareup.leakcanary:leakcanary-android:2.4")

        // ViewPager 2
        implementation("androidx.viewpager2:viewpager2:1.0.0")

        // ViewPager Circle Indicator
        implementation("me.relex:circleindicator:2.1.4")

        // AndroidX Media
        implementation("androidx.media:media:1.2.0")

        // AndroidX Preference
        implementation("androidx.preference:preference:1.1.1")
        implementation("androidx.preference:preference-ktx:1.1.1")

        // ChromeCast
        implementation("com.google.android.gms:play-services-cast-framework:19.0.0")

        // NanoHttp
        implementation("org.nanohttpd:nanohttpd-webserver:2.3.1")

        // Glide
        implementation("com.github.bumptech.glide:glide:4.11.0")
        kapt("com.github.bumptech.glide:compiler:4.11.0")
        implementation("com.github.bumptech.glide:okhttp3-integration:4.11.0")

        // Moshi
        kapt("com.squareup.moshi:moshi-kotlin-codegen:1.9.3")

        // AndroidX Lifecycle
        implementation("androidx.lifecycle:lifecycle-common-java8:2.2.0")

        // Noise
        implementation("com.github.paramsen:noise:2.0.0")

        // MpAndroidChart
        implementation("com.github.PhilJay:MPAndroidChart:3.1.0")

        // BugSnag
        implementation("com.bugsnag:bugsnag-android:5.0.1")

        // AndroidX Lifecycle
        implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.3.0-beta01")

        compile("com.github.bumptech.glide:recyclerview-integration:4.11.0") {
            // Excludes the support library because it's already included by Glide.
            isTransitive = false
        }

        // ExoPlayer
        implementation(project(":exoplayer-library-core"))
        implementation(project(":exoplayer-library-hls"))

        implementation("androidx.drawerlayout:drawerlayout:1.1.1")
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