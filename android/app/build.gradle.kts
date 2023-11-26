plugins {
    id("com.android.application")
    id("kotlin-android")
    id("androidx.navigation.safeargs.kotlin")
    id("com.mikepenz.aboutlibraries.plugin")
    id("kotlin-parcelize")
    id("dagger.hilt.android.plugin")
    id("com.google.firebase.crashlytics")
    id("com.google.devtools.ksp")
}

android {

    compileSdk = 34

    defaultConfig {
        applicationId = "com.simplecityapps.shuttle"
        minSdk = 21
        targetSdk = 34
        versionName = versionName()
        versionCode = versionCode()
        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "com.simplecityapps.shuttle.CustomTestRunner"
        ndk {
            debugSymbolLevel = "FULL"
        }
    }

    signingConfigs {
        create("release") {
            if (isCiBuild() && isReleaseBuild()) {
                val keystore = file("../keystore.ks")
                if (!keystore.exists()) {
                    throw Exception("Missing keystore.jks")
                }
                storeFile = keystore
                storePassword = getEnv("KEYSTORE_PASSWORD")
                keyAlias = getEnv("KEYSTORE_ALIAS")
                keyPassword = getEnv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".dev"
            if (isCiBuild()) {
                // We want proguard enabled on CI, so our instrumented tests run on obfuscated code
                isMinifyEnabled = true
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            }
        }
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    packaging {
        resources {
            resources.excludes += setOf("META-INF/*.kotlin_module")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
        freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.RequiresOptIn"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    namespace = "com.simplecityapps.shuttle"

    dependencies {
        implementation(fileTree("dir" to "libs", "include" to listOf("*.jar")))

        // Shuttle Core
        implementation(project(":android:core"))

        // Shuttle Networking
        implementation(project(":android:networking"))

        // TagLib
        implementation("com.github.timusus:ktaglib:1.4.1")

        // Shuttle
        implementation(project(":android:data"))
        implementation(project(":android:mediaprovider:core"))
        implementation(project(":android:mediaprovider:local"))
        implementation(project(":android:mediaprovider:emby"))
        implementation(project(":android:mediaprovider:jellyfin"))
        implementation(project(":android:mediaprovider:plex"))

        // Shuttle Image Loader
        implementation(project(":android:imageloader"))

        // Shuttle Playback
        implementation(project(":android:playback"))

        // RecylerView Adapter
        implementation(project(":android:recyclerview-adapter"))

        // Storage Access Framework Helper
        implementation(project(":android:saf"))

        // Trial
        implementation(project(":android:trial"))

        // RecyclerView FastScroll
        implementation("com.github.timusus:RecyclerView-FastScroll:dev-SNAPSHOT")

        // AppCompat
        implementation("androidx.appcompat:appcompat:1.6.1")

        // Material
        implementation("com.google.android.material:material:1.10.0")


        // Constraint Layout
        implementation("androidx.constraintlayout:constraintlayout:2.1.4")

        // Android Arch
        implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")

        // AndroidX Navigation
        implementation("androidx.navigation:navigation-fragment-ktx:2.7.5")
        implementation("androidx.navigation:navigation-ui-ktx:2.7.5")

        // Hilt
        implementation("com.google.dagger:hilt-android:2.48.1")
        ksp("com.google.dagger:hilt-compiler:2.48.1")

        androidTestImplementation("com.google.dagger:hilt-android-testing:2.48.1")
        kspAndroidTest("com.google.dagger:hilt-android-compiler:2.48.1")

        // Leak Canary
        debugImplementation("com.squareup.leakcanary:leakcanary-android:2.10")

        // ViewPager 2
        implementation("androidx.viewpager2:viewpager2:1.1.0-beta02")

        // ViewPager Circle Indicator
        implementation("me.relex:circleindicator:2.1.4")

        // AndroidX Media
        implementation("androidx.media:media:1.6.0")

        // AndroidX Preference
        implementation("androidx.preference:preference-ktx:1.2.1")

        // ChromeCast
        implementation("com.google.android.gms:play-services-cast-framework:21.3.0")

        // NanoHttp
        implementation("org.nanohttpd:nanohttpd-webserver:2.3.1")

        // Moshi
        ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.0")

        // AndroidX Lifecycle
        implementation("androidx.lifecycle:lifecycle-common-java8:2.6.2")

        // Noise
        implementation("com.github.paramsen:noise:2.0.0")

        // MpAndroidChart
        implementation("com.github.PhilJay:MPAndroidChart:3.1.0")

        // AndroidX Lifecycle
        implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")

        // ExoPlayer
        implementation("com.github.timusus.exoplayer:exoplayer-core:2.14.2-shuttle")
        implementation("com.github.timusus.exoplayer:exoplayer-hls:2.14.2-shuttle")

        implementation("androidx.drawerlayout:drawerlayout:1.2.0")

        // New fragment manager
        implementation("androidx.fragment:fragment-ktx:1.6.2")

        // Glide
        implementation("com.github.bumptech.glide:glide:4.16.0")
        implementation("com.github.bumptech.glide:okhttp3-integration:4.16.0")
        implementation("com.github.bumptech.glide:recyclerview-integration:4.14.2") {
            // Excludes the support library because it's already included by Glide.
            isTransitive = false
        }

        // About Libraries
        implementation("com.mikepenz:aboutlibraries-core:10.9.2")

        // Billing
        implementation("com.android.billingclient:billing-ktx:6.1.0")

        // Play Core (review api)
        implementation("com.google.android.play:core:1.10.3")
        implementation("com.google.android.play:core-ktx:1.8.1")

        // Semantic versioning
        implementation("com.vdurmont:semver4j:3.1.0")

        implementation("com.microsoft.design:fluent-system-icons:1.1.137@aar")

        // KotlinX DateTime
        implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
        // Core Library Desugaring - Required for KotlinX DateTime on API < 27
        coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

        // Firebase
        implementation(platform("com.google.firebase:firebase-bom:32.6.0"))
        implementation("com.google.firebase:firebase-crashlytics")


        // Testing
        androidTestImplementation("androidx.test:runner:1.5.2")
        androidTestImplementation("androidx.test:rules:1.5.0")
        androidTestImplementation("androidx.test:core-ktx:1.5.0")
        androidTestImplementation("org.hamcrest:hamcrest-library:1.3")

        // Remote config
        implementation(project(":android:remote-config"))


        testImplementation("junit:junit:4.13.2")

        // WorkManager
        implementation("androidx.work:work-runtime-ktx:2.9.0-rc01")
        implementation("androidx.hilt:hilt-work:1.1.0")
        ksp("androidx.hilt:hilt-compiler:1.1.0")
    }

    buildFeatures.buildConfig = true
}

apply(plugin = "com.google.gms.google-services")

/**
 * Retrieves an Environment Variable, or throws [MissingEnvVarException]
 */
fun getEnv(name: String): String {
    return System.getenv(name) ?: throw MissingEnvVarException(name)
}

fun isCiBuild(): Boolean {
    return try {
        getEnv("CI").toBoolean()
    } catch (e: MissingEnvVarException) {
        println("'CI' Environment Variable not found. This build is presumed to be a non-CI build.")
        false
    }
}

fun isReleaseBuild(): Boolean {
    return try {
        getEnv("CONFIGURATION") == "Release"
    } catch (e: MissingEnvVarException) {
        println("'CONFIGURATION' Environment Variable not found. This build is presumed to be a Debug build.")
        false
    }
}

fun versionName(): String {
    return "${AppVersion.versionMajor}.${AppVersion.versionMinor}.${AppVersion.versionPatch}${if (AppVersion.versionSuffix != null) "-${AppVersion.versionSuffix}" else ""} (${versionCode()})"
}

fun versionCode(): Int {
    // Major + minor + CI build number (where available)
    return (AppVersion.versionMajor * 10000000) + (AppVersion.versionMinor * 1000000) + (AppVersion.versionPatch * 10000) +
            when {
                isCiBuild() -> getEnv("GITHUB_RUN_NUMBER").toInt() + 20 // Add 20 due to move from Jenkins to GH Actions
                else -> 1
            }
}

class MissingEnvVarException(private val name: String) : Exception() {
    override val message: String
        get() = "Missing Environment Variable: $name"
}
