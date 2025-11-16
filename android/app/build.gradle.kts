import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("androidx.navigation.safeargs.kotlin")
    id("com.mikepenz.aboutlibraries.plugin")
    id("kotlin-parcelize")
    id("dagger.hilt.android.plugin")
    id("com.google.firebase.crashlytics")
    id("com.google.devtools.ksp")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.detekt)
    alias(libs.plugins.paparazzi)
}

android {

    compileSdk = 36

    defaultConfig {
        applicationId = "com.simplecityapps.shuttle"
        minSdk = 23
        targetSdk = 36
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
                val keystore = file("./keystore.ks")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin.compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-Xopt-in=kotlin.RequiresOptIn",
            "-Xopt-in=kotlin.time.ExperimentalTime"
        )
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    buildFeatures {
        compose = true
    }

    namespace = "com.simplecityapps.shuttle"

    dependencies {
        implementation(fileTree("dir" to "libs", "include" to listOf("*.jar")))

        val composeBom = platform(libs.androidx.compose.bom)
        implementation(composeBom)
        androidTestImplementation(composeBom)
        implementation(libs.androidx.activity.ktx)
        implementation(libs.androidx.activity.compose)
        implementation(libs.androidx.hilt.navigation.compose)
        testImplementation(libs.cashapp.paparazzi)
        testImplementation(libs.test.parameter.injector)
        testImplementation(libs.compose.preview.scanner)
        implementation(libs.kotlinx.collections.immutable)
        implementation(libs.kotlinx.datetime)
        implementation(libs.androidx.material3)
        implementation("androidx.compose.material:material-icons-extended")

        // Android Studio Preview support
        implementation(libs.androidx.ui.tooling.preview)
        debugImplementation(libs.androidx.ui.tooling)

        implementation(libs.androidx.lifecycle.viewmodel.compose)

        // Shuttle Core
        implementation(project(":android:core"))

        // Shuttle Networking
        implementation(project(":android:networking"))

        // TagLib
        implementation(libs.timusus.ktaglib)

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
        implementation(libs.timusus.recyclerViewFastScroll)

        // AppCompat
        implementation(libs.androidx.appcompat)

        // Material
        implementation(libs.google.material)


        // Constraint Layout
        implementation(libs.androidx.constraintlayout)

        // Android Arch
        implementation(libs.androidx.lifecycle.extensions)

        // AndroidX Navigation
        implementation(libs.androidx.navigation.fragment.ktx)
        implementation(libs.androidx.navigation.ui.ktx)

        // Hilt
        implementation(libs.hilt)
        ksp(libs.hilt.compiler)

        androidTestImplementation(libs.hilt.android.testing)
        kspAndroidTest(libs.hilt.compiler)

        // Leak Canary
        debugImplementation(libs.leakcanary.android)

        // ViewPager 2
        implementation(libs.androidx.viewpager2)

        // ViewPager Circle Indicator
        implementation(libs.relex.circleindicator)

        // AndroidX Media
        implementation(libs.androidx.media)

        // AndroidX Preference
        implementation(libs.androidx.preference.ktx)

        // ChromeCast
        implementation(libs.google.play.services.cast.framework)

        // NanoHttp
        implementation(libs.nanohttpd.webserver)

        // Moshi
        ksp(libs.moshi.kotlinCodegen)

        // AndroidX Lifecycle
        implementation(libs.androidx.lifecycle.common.java8)

        // Noise
        implementation(libs.paramsen.noise)

        // MpAndroidChart
        implementation(libs.philjay.mpAndroidChart)

        // AndroidX Lifecycle
        implementation(libs.androidx.lifecycle.runtime.ktx)

        // ExoPlayer
        implementation(libs.exoplayer.core)
        implementation(libs.exoplayer.hls)

        implementation(libs.androidx.drawerlayout)

        // New fragment manager
        implementation(libs.androidx.fragment.ktx)

        // Glide
        implementation(libs.glide)
        implementation(libs.glide.okhttp3Integration)
        implementation("com.github.bumptech.glide:recyclerview-integration:4.14.2") {
            // Excludes the support library because it's already included by Glide.
            isTransitive = false
        }

        // About Libraries
        implementation(libs.mikepenz.aboutlibrariesCore)

        // Billing
        implementation(libs.billingclient.billingKtx)

        // Play Core (review api)
        implementation(libs.google.review)
        implementation(libs.google.review.ktx)

        // Semantic versioning
        implementation(libs.vdurmont.semver4j)

        implementation(libs.design.fluentSystemIcons)

        // KotlinX DateTime
        implementation(libs.kotlinx.datetime)
        // Core Library Desugaring - Required for KotlinX DateTime on API < 27
        coreLibraryDesugaring(libs.tools.desugar.jdk.libs)

        // Firebase
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.crashlytics)

        // Testing
        androidTestImplementation(libs.androidx.runner)
        androidTestImplementation(libs.androidx.rules)
        androidTestImplementation(libs.androidx.core.ktx)
        androidTestImplementation(libs.hamcrest.library)

        // Remote config
        implementation(project(":android:remote-config"))


        testImplementation(libs.junit)

        // WorkManager
        implementation(libs.androidx.work.runtime.ktx)
        implementation(libs.androidx.hilt.work)
        ksp(libs.androidx.hilt.compiler)

        detektPlugins(libs.detekt.formatting)

        lintChecks(libs.compose.lint.checks)
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
    return "${AppVersion.versionMajor}.${AppVersion.versionMinor}.${AppVersion.versionPatch}${if (!AppVersion.versionSuffix.isNullOrEmpty()) "-${AppVersion.versionSuffix}" else ""} (${versionCode()})"
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
