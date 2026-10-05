package com.simplecityapps.shuttle.baselineprofile

import android.view.KeyEvent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/*
 * The journeys the Baseline Profile covers and the benchmarks measure, found by the Compose test tags MainActivity
 * exposes as resource ids, and by visible text where a control has no tag.
 */

private const val TIMEOUT_MS = 10_000L

/** The first library import, on a fresh install, reads every sample song's tags; later launches only load them. */
private const val FIRST_IMPORT_TIMEOUT_MS = 120_000L

/** Home's content: Jump back in once something has played, else the cold-start Shuffle all. */
private val homeContent: BySelector = By.res(Pattern.compile("homeGrid\\.cell|home\\.shuffleAll"))

/** The start tab's content: Library by default (Settings' Show Home on launch is off), else Home. */
private val launchContent: BySelector =
    By.res(Pattern.compile("homeGrid\\.cell|home\\.shuffleAll|library-(songs|albums|artists|genres|playlists|folders)"))

fun MacrobenchmarkScope.waitForLaunchContent(timeoutMs: Long = TIMEOUT_MS) {
    check(device.wait(Until.hasObject(launchContent), timeoutMs)) { "The start tab showed no content within ${timeoutMs}ms; ${screenSummary()}" }
}

fun MacrobenchmarkScope.openHome() {
    device.findObject(By.text("Home")).click()
    check(device.wait(Until.hasObject(homeContent), TIMEOUT_MS)) { "Home showed no content within ${TIMEOUT_MS}ms; ${screenSummary()}" }
}

/** What's on screen, for a failure message: the visible texts and resource ids, and how many songs MediaStore has. */
private fun MacrobenchmarkScope.screenSummary(): String {
    val texts = device.findObjects(By.text(Pattern.compile(".+"))).mapNotNull { it.text }
    val ids = device.findObjects(By.res(Pattern.compile(".+"))).mapNotNull { it.resourceName }.distinct()
    val songs = device.executeShellCommand("content query --uri content://media/external/audio/media --projection _id").lines().count { it.startsWith("Row") }
    return "MediaStore songs: $songs; texts: $texts; ids: $ids"
}

private var libraryPrepared = false

/**
 * Seeds the library, then launches once and waits for the first import, so every launch after it has content. Once per
 * test run: the compilation warm-up runs a journey before its first measured iteration, so `iteration` can't tell.
 */
fun MacrobenchmarkScope.prepareLibrary() {
    if (libraryPrepared) return
    libraryPrepared = true
    SampleLibrary.seed()
    pressHome()
    startActivityAndWait()
    waitForLaunchContent(FIRST_IMPORT_TIMEOUT_MS)
}

fun MacrobenchmarkScope.browseLibrary() {
    device.findObject(By.text("Library")).click()
    require(By.res("library-pager"))
    device.findObject(By.res("library-sections")).findObject(By.text("Songs")).click()
    require(By.res("library-songs")).flingDownThenUp()
    device.findObject(By.res("library-sections")).findObject(By.text("Albums")).click()
    require(By.res("library-albums")).flingDownThenUp()
}

/** Opens the first album, from Library's Albums page, plays it and opens the player, which it leaves open: the last journey. */
fun MacrobenchmarkScope.openAlbumAndPlay() {
    require(By.text("Library")).click()
    // The grid's first item is the sort/controls row, then the albums
    require(By.res("library-albums")).children[1].click()
    require(By.text("Play")).click()
    require(By.res("player_mini")).click()
    require(By.res("player_now_playing"))
    device.pressKeyCode(KeyEvent.KEYCODE_MEDIA_PAUSE)
}

fun MacrobenchmarkScope.search() {
    require(By.text("Search")).click()
    val field = require(By.clazz("android.widget.EditText"))
    field.click()
    field.text = "harbour"
    require(By.text("Harbour Weather"))
    device.pressBack()
}

private fun MacrobenchmarkScope.require(selector: BySelector): UiObject2 = device.wait(Until.findObject(selector), TIMEOUT_MS) ?: error("Not found: $selector; ${screenSummary()}")

private fun UiObject2.flingDownThenUp() {
    // Keep clear of the system gesture areas
    setGestureMargin(visibleBounds.height() / 5)
    fling(Direction.DOWN)
    fling(Direction.UP)
}
