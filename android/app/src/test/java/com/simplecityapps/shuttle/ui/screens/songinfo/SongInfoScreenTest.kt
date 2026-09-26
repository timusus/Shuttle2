package com.simplecityapps.shuttle.ui.screens.songinfo

import androidx.compose.ui.test.junit4.createComposeRule
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SongInfoScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = SongInfoRobot(composeTestRule)

    @Test
    fun `shows the song's tags and file details`() {
        val song = sampleSongWithFileDetails()
        robot.setState(songInfoReady(song))

        robot.assertTextDisplayed("Title")
        robot.assertTextDisplayed(song.album!!)
        robot.assertTextDisplayed("Play count")
        robot.assertTextDisplayed("12")
        robot.assertTextDisplayed("Music/${song.albumArtist}/${song.album}/01 ${song.name}.flac")
        robot.assertTextDisplayed("30.00 MB")
        robot.assertTextDisplayed("96 kHz")
        robot.assertTextDisplayed("24-bit")
        robot.assertTextDisplayed("-7.25 dB")
    }

    @Test
    fun `the header shows the format, bit rate and sample rate as chips`() {
        robot.setState(songInfoReady())

        robot.assertTextDisplayedWithoutScrolling("FLAC")
        robot.assertTextDisplayedWithoutScrolling("1024 kb/s")
        robot.assertTextDisplayedWithoutScrolling("96 kHz")
    }

    @Test
    fun `details are grouped into tags, file and playback cards`() {
        robot.setState(songInfoReady())

        robot.assertTextDisplayed("Tags")
        robot.assertTextDisplayed("File")
        robot.assertTextDisplayed("Playback")
    }

    @Test
    fun `a missing value reads Unknown`() {
        robot.setState(songInfoReady())

        // The sample song has no lyrics.
        robot.assertTextDisplayed("Lyrics")
        robot.assertTextDisplayed("Unknown")
    }

    @Test
    fun `copy path copies the readable path`() {
        val song = sampleSongWithFileDetails()
        robot.setState(songInfoReady(song))

        robot.clickCopyPath()

        robot.copiedPath shouldBe song.displayPath
    }

    @Test
    fun `back navigates up`() {
        robot.setState(songInfoReady())

        robot.clickBack()

        robot.navigatedUp shouldBe true
    }

    @Test
    fun `a song no longer in the library says so`() {
        robot.setState(songInfoNotFound)

        robot.assertTextDisplayedWithoutScrolling("This song is no longer in your library")
    }
}
