package com.simplecityapps.shuttle.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's Baseline Profile and startup profile from its main journeys: cold start to the start tab, Home, Library's songs
 * and albums, Search, then an album, playback and the player. Run with `./gradlew :android:app:generateBaselineProfile`.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE) {
        prepareLibrary()
        pressHome()
        startActivityAndWait()
        waitForLaunchContent()
        openHome()
        browseLibrary()
        search()
        openAlbumAndPlay()
    }
}
