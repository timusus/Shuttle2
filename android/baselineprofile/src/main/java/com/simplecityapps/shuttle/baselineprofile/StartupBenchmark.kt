package com.simplecityapps.shuttle.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start to the start tab's content (TTID, and TTFD from Library's or Home's ReportDrawnWhen), without and with the
 * Baseline Profile, plus
 * the Application.onCreate and per-initializer trace sections. Run with
 * `./gradlew :android:baselineprofile:pixel6Api34BenchmarkReleaseAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupWithoutProfile() = startup(CompilationMode.None())

    @Test
    fun startupWithProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    @OptIn(ExperimentalMetricApi::class)
    private fun startup(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            StartupTimingMetric(),
            TraceSectionMetric("S2 Application.onCreate"),
            TraceSectionMetric("S2 init %", TraceSectionMetric.Mode.Sum),
        ) + APPLICATION_SECTIONS.map { TraceSectionMetric(it) },
        compilationMode = compilationMode,
        startupMode = StartupMode.COLD,
        iterations = 15,
        setupBlock = {
            prepareLibrary()
            pressHome()
        },
    ) {
        startActivityAndWait()
        waitForLaunchContent()
    }
}

/** ShuttleApplication.onCreate's steps, and each AppInitializer (their release names survive R8: proguard-rules.pro). */
private val APPLICATION_SECTIONS = listOf("S2 app inject", "S2 app setDayNightMode", "S2 app installDefaults") +
    listOf(
        "Telemetry", "Timber", "Playback", "PlaybackReporting", "Scrobbling", "Widget", "MediaProvider", "Entitlement",
        "Shortcut", "Downloads", "Appearance", "SearchIndex", "FavouriteSync",
    ).map { "S2 init ${it}Initializer" }
