package com.simplecityapps.shuttle.ui.screens.library

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyTaskType
import com.simplecityapps.shuttle.designsystem.theme.S2Theme
import com.simplecityapps.shuttle.fixtures.SampleLibrary
import com.simplecityapps.shuttle.model.OtherRecording
import com.simplecityapps.shuttle.ui.DocsDesignRoborazziOptions
import com.simplecityapps.shuttle.ui.SampleArtworkCoil
import com.simplecityapps.shuttle.ui.preview.toSong
import java.io.File
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Records the "Other recordings" list into `docs/design/library/`; a no-op unless Roborazzi is recording or verifying. */
@RunWith(RobolectricTestRunner::class)
class OtherRecordingsScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun skipUnlessRoborazziActive() = assumeTrue(roborazziSystemPropertyTaskType().isEnabled())

    @Before
    fun installSampleArtwork() = SampleArtworkCoil.install(ApplicationProvider.getApplicationContext())

    @After
    fun uninstallSampleArtwork() = SampleArtworkCoil.uninstall()

    @Test
    fun otherRecordings() {
        val recordings = SampleLibrary.albums.take(3).mapIndexed { index, album ->
            val song = SampleLibrary.songs.first { it.albumId == album.id }.toSong().copy(date = LocalDate(2015 - index * 12, 1, 1), conductor = album.artist)
            OtherRecording(song.albumGroupKey, song.album, song.albumArtist, listOf(song))
        }
        composeTestRule.setContent {
            S2Theme {
                Surface {
                    Column {
                        OtherRecordingsContent(title = "Sonata No. 3", recordings = recordings, onOpen = {}, onDismissRequest = {})
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = File(shotsDir, "other-recordings.png").path,
            roborazziOptions = DocsDesignRoborazziOptions,
        )
    }

    private companion object {
        val shotsDir: File by lazy {
            generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .first { File(it, "gradlew").exists() && File(it, "docs/design").isDirectory }
                .resolve("docs/design/library")
        }
    }
}
