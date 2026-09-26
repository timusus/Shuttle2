import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("com.mikepenz.aboutlibraries.plugin.android")
    id("dagger.hilt.android.plugin")
    alias(libs.plugins.sentry)
    id("com.google.devtools.ksp")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.kotlin.serialization)
}

// Local development keys (see [secret]); declared before `android`, which reads them while the script runs
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {

    compileSdk = 37

    defaultConfig {
        applicationId = "com.simplecityapps.shuttle"
        // 24: Compose 1.13, which material3 1.5.0-alpha29 brings in, no longer supports API 23.
        minSdk = 24
        targetSdk = 36
        versionName = versionName()
        versionCode = versionCode()
        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "com.simplecityapps.shuttle.CustomTestRunner"
        ndk {
            debugSymbolLevel = "FULL"
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }

        // Crash reporting (Sentry) and product analytics (PostHog). Never committed: each comes from local.properties
        // or a Gradle property (local builds), else an environment variable (CI secrets). Blank, the SDK is never
        // started, so local builds and tests send nothing.
        buildConfigField("String", "SENTRY_DSN", "\"${secret("sentry.dsn", "SENTRY_DSN").orEmpty()}\"")
        buildConfigField("String", "POSTHOG_API_KEY", "\"${secret("posthog.api.key", "POSTHOG_API_KEY").orEmpty()}\"")
        buildConfigField("String", "POSTHOG_HOST", "\"${secret("posthog.host", "POSTHOG_HOST") ?: "https://eu.i.posthog.com"}\"")
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

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
        managedDevices {
            localDevices {
                create("pixel6Api34Atd") {
                    device = "Pixel 6"
                    apiLevel = 34
                    systemImageSource = "aosp-atd"
                }
            }
            groups {
                create("smoke") {
                    targetDevices.add(localDevices["pixel6Api34Atd"])
                }
            }
        }
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        animationsDisabled = true
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
        testImplementation(libs.roborazzi)
        testImplementation(libs.roborazzi.compose)
        implementation(libs.kotlinx.collections.immutable)
        implementation(libs.kotlinx.datetime)
        implementation(libs.androidx.material3)
        implementation(libs.androidx.glance.appwidget)
        implementation(libs.androidx.glance.material3)
        implementation("androidx.compose.material:material-icons-extended")

        // Android Studio Preview support
        implementation(libs.androidx.ui.tooling.preview)
        debugImplementation(libs.androidx.ui.tooling)

        implementation(libs.androidx.lifecycle.viewmodel.compose)

        // Navigation 3 and adaptive layouts for the Compose shell (docs/architecture/app-shell.md)
        implementation(libs.androidx.navigation3.runtime)
        implementation(libs.androidx.navigation3.ui)
        implementation(libs.androidx.lifecycle.viewmodel.navigation3)
        implementation(libs.androidx.material3.adaptive)
        implementation(libs.androidx.material3.adaptive.layout)
        implementation(libs.androidx.material3.adaptive.navigation3)
        implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
        implementation(libs.kotlinx.serialization.core)

        // Shuttle Core
        implementation(project(":android:core"))

        // Design system (S2Theme, components, and the debug-only catalogue screen)
        implementation(project(":android:designsystem"))

        // Shuttle Networking
        implementation(project(":android:networking"))

        // TagLib
        implementation(libs.timusus.ktaglib)

        // Shuttle
        implementation(project(":android:domain"))
        implementation(project(":android:downloads"))
        implementation(project(":android:mediaprovider:core"))
        implementation(project(":android:mediaprovider:local"))
        implementation(project(":android:mediaprovider:emby"))
        implementation(project(":android:mediaprovider:jellyfin"))
        implementation(project(":android:mediaprovider:plex"))

        // Shuttle Image Loader
        implementation(project(":android:imageloader"))

        // Shuttle Playback
        implementation(project(":android:playback"))

        // Storage Access Framework Helper
        implementation(project(":android:saf"))

        // Trial
        implementation(project(":android:trial"))

        // AppCompat
        implementation(libs.androidx.appcompat)

        // Material
        implementation(libs.google.material)

        // Hilt
        implementation(libs.hilt)
        ksp(libs.hilt.compiler)

        androidTestImplementation(libs.hilt.android.testing)
        kspAndroidTest(libs.hilt.compiler)

        // Leak Canary
        debugImplementation(libs.leakcanary.android)

        // AndroidX Preference: SettingsRepositoriesTest checks the settings still open its file
        testImplementation(libs.androidx.preference.ktx)

        // ChromeCast
        implementation(libs.google.play.services.cast.framework)

        // Moshi
        ksp(libs.moshi.kotlinCodegen)

        // AndroidX Lifecycle
        implementation(libs.androidx.lifecycle.common.java8)

        // AndroidX Lifecycle
        implementation(libs.androidx.lifecycle.runtime.ktx)

        // Media3 ExoPlayer
        implementation(libs.media3.exoplayer)
        implementation(libs.media3.exoplayerHls)
        // Media3 doesn't publish its FLAC and Opus decoders; support/scripts/build-media3-decoders.sh
        // builds these (16 KB page-aligned) from androidx/media at the catalog's media3 version.
        implementation(files("libs/media3-decoder-flac-1.11.1.aar"))
        implementation(files("libs/media3-decoder-opus-1.11.1.aar"))

        // New fragment manager
        implementation(libs.androidx.fragment.ktx)

        // Coil
        implementation(libs.coil.compose)

        // Drag to reorder in lazy lists (playlist detail)
        implementation(libs.reorderable)

        // About Libraries
        implementation(libs.mikepenz.aboutlibrariesCore)

        // Play Core (review api)
        implementation(libs.google.review)
        implementation(libs.google.review.ktx)

        // Semantic versioning
        implementation(libs.vdurmont.semver4j)

        // KotlinX DateTime
        implementation(libs.kotlinx.datetime)
        // Core Library Desugaring - Required for KotlinX DateTime on API < 27
        coreLibraryDesugaring(libs.tools.desugar.jdk.libs)

        // Crash reporting and product analytics, both off until the user opts in (TelemetryConsentGate)
        implementation(libs.sentry.android)
        implementation(libs.posthog.android)

        // Testing
        testImplementation(libs.kotest)
        testImplementation(libs.mockk)
        testImplementation(libs.kotlinx.coroutinesTest)
        testImplementation(libs.robolectric)
        // The sample library: invented names and generated covers for screenshot tests and @Previews
        // (ui/preview). Release compiles against it, for the previews in main source, but never
        // packages it, and R8 does not catch live code that reaches it: keep fixture use inside
        // @Preview functions.
        testImplementation(project(":android:fixtures"))
        debugImplementation(project(":android:fixtures"))
        releaseCompileOnly(project(":android:fixtures"))
        testImplementation(libs.androidx.glance.appwidget.testing)
        testImplementation("androidx.compose.ui:ui-test-junit4")
        debugImplementation("androidx.compose.ui:ui-test-manifest")
        androidTestImplementation("androidx.compose.ui:ui-test-junit4")
        androidTestImplementation(libs.androidx.runner)
        androidTestImplementation(libs.androidx.rules)
        androidTestImplementation(libs.androidx.core.ktx)
        // Declared directly, not just through ui-test-junit4, or its error_prone_annotations 2.30.0 clashes with the
        // app runtime classpath's strict 2.28.0 and the androidTest classpath fails to resolve
        androidTestImplementation(libs.androidx.espresso.core)
        androidTestImplementation(libs.androidx.junit)
        androidTestImplementation(libs.androidx.ui.test.junit4)
        debugImplementation(libs.androidx.ui.test.manifest)
        androidTestUtil("androidx.test:orchestrator:1.5.1")

        testImplementation(libs.junit)

        // WorkManager
        implementation(libs.androidx.work.runtime.ktx)
        implementation(libs.androidx.hilt.work)
        ksp(libs.androidx.hilt.compiler)

        lintChecks(libs.compose.lint.checks)
    }

    buildFeatures.buildConfig = true
}

// Hilt's hiltJavaCompile<Variant> task runs javac over a processor path (hiltAnnotationProcessor<Variant>)
// that extends the variant's ksp configuration. javac then discovers moshi-kotlin-codegen's legacy APT
// processor and prints its "Kapt support ... is deprecated" warning. Moshi codegen already runs via KSP,
// so drop it from that javac processor path only.
configurations.matching { it.name.startsWith("hiltAnnotationProcessor") }.configureEach {
    exclude(group = "com.squareup.moshi", module = "moshi-kotlin-codegen")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-Xopt-in=kotlin.RequiresOptIn",
            "-Xopt-in=kotlin.time.ExperimentalTime"
        )
    }
}

// Uploads the R8 mapping so release stack traces read, but only when CI provides the auth token
sentry {
    val token = System.getenv("SENTRY_AUTH_TOKEN")
    includeProguardMapping = !token.isNullOrEmpty()
    autoUploadProguardMapping = !token.isNullOrEmpty()
    org = "simplecity-apps"
    projectName = "s2-android"
    authToken = token
    // The SDK is declared above, and nothing uses tracing, so no bytecode instrumentation either
    autoInstallation.enabled = false
    tracingInstrumentation.enabled = false
    telemetry = false
}

/** A key from local.properties or a Gradle property, else the [envName] environment variable; null when none is set. */
fun secret(name: String, envName: String): String? = (
    localProperties.getProperty(name)
        ?: providers.gradleProperty(name).orNull
        ?: System.getenv(envName)
    )?.takeIf { it.isNotBlank() }

/**
 * Retrieves an Environment Variable, or throws [MissingEnvVarException]
 */
fun getEnv(name: String): String = System.getenv(name) ?: throw MissingEnvVarException(name)

fun isCiBuild(): Boolean = try {
    getEnv("CI").toBoolean()
} catch (e: MissingEnvVarException) {
    println("'CI' Environment Variable not found. This build is presumed to be a non-CI build.")
    false
}

fun isReleaseBuild(): Boolean = try {
    getEnv("CONFIGURATION") == "Release"
} catch (e: MissingEnvVarException) {
    println("'CONFIGURATION' Environment Variable not found. This build is presumed to be a Debug build.")
    false
}

// Tag format: vYYMMDDNN (e.g., v26032801 -> versionCode 26032801, versionName 2026.03.28)
fun getVersionFromGitTag(): Pair<Int, String> {
    // providers.exec (rather than ProcessBuilder) keeps the configuration cache valid: Gradle
    // re-runs the command on each build and invalidates the cache when the tag changes.
    val tag = try {
        providers.exec {
            commandLine("git", "describe", "--tags", "--abbrev=0", "--match", "v[0-9]*")
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim().removePrefix("v")
    } catch (_: Exception) {
        null
    } ?: return 1 to "1.0.0"

    val code = tag.toIntOrNull() ?: return 1 to "1.0.0"
    val name = if (tag.length >= 6) {
        val year = "20${tag.substring(0, 2)}"
        val month = tag.substring(2, 4)
        val day = tag.substring(4, 6)
        "$year.$month.$day"
    } else {
        "1.0.0"
    }
    return code to name
}

fun versionName(): String = findProperty("versionName")?.toString() ?: getVersionFromGitTag().second

fun versionCode(): Int = findProperty("versionCode")?.toString()?.toIntOrNull() ?: getVersionFromGitTag().first

roborazzi {
    outputDir.set(file("src/test/snapshots/images"))
}

class MissingEnvVarException(private val name: String) : Exception() {
    override val message: String
        get() = "Missing Environment Variable: $name"
}
