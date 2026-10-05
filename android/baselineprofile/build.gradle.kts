import javax.inject.Inject
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Generates :android:app's Baseline Profile and measures its cold start, on a Gradle Managed Device
// (docs/performance/android-startup.md). Regenerate before each release:
//   ./gradlew :android:app:generateReleaseBaselineProfile
plugins {
    id("com.android.test")
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.simplecityapps.shuttle.baselineprofile"
    compileSdk = 37

    defaultConfig {
        // Baseline Profile generation needs 28+ (33+ without root); the GMD below runs 34.
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The managed device is an emulator: its timings compare runs with each other, not with a phone
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":android:app"

    testOptions.managedDevices.localDevices {
        // Not an ATD image: those strip the system services profile collection needs.
        create("pixel6Api34") {
            device = "Pixel 6"
            apiLevel = 34
            systemImageSource = "aosp"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

baselineProfile {
    managedDevices += "pixel6Api34"
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

/**
 * The screenshot tests' sample library (16 albums, 97 tracks with embedded covers, 4 playlists), built with ffmpeg by
 * seed-test-media.sh and packaged into the test APK, which copies it onto the device so the journeys have a library.
 */
abstract class GenerateSampleLibrary : DefaultTask() {
    @get:Inject abstract val execOperations: ExecOperations

    @get:Inject abstract val fileSystemOperations: FileSystemOperations

    @get:InputFile abstract val script: RegularFileProperty

    @get:Internal abstract val cacheDir: DirectoryProperty

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        execOperations.exec { commandLine(script.get().asFile.absolutePath, "library", "--generate-only") }
        fileSystemOperations.sync {
            from(cacheDir)
            into(outputDir.dir("sample-library"))
        }
    }
}

val generateSampleLibrary = tasks.register<GenerateSampleLibrary>("generateSampleLibrary") {
    script.set(rootProject.layout.projectDirectory.file("support/scripts/seed-test-media.sh"))
    cacheDir.set(rootProject.layout.projectDirectory.dir("build/test-media/library"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(generateSampleLibrary, GenerateSampleLibrary::outputDir)
    }
}
