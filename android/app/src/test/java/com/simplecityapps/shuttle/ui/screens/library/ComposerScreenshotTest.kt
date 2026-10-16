package com.simplecityapps.shuttle.ui.screens.library

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyTaskType
import com.simplecityapps.shuttle.designsystem.theme.S2Theme
import com.simplecityapps.shuttle.fixtures.SampleLibrary
import com.simplecityapps.shuttle.model.ComposerWork
import com.simplecityapps.shuttle.model.ComposerWorks
import com.simplecityapps.shuttle.model.OtherRecording
import com.simplecityapps.shuttle.ui.DocsDesignRoborazziOptions
import com.simplecityapps.shuttle.ui.SampleArtworkCoil
import com.simplecityapps.shuttle.ui.preview.toAlbum
import com.simplecityapps.shuttle.ui.preview.toSong
import com.simplecityapps.shuttle.ui.screens.library.composer.ComposerUiState
import java.io.File
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Records the composer page into `docs/design/library/`; a no-op unless Roborazzi is recording or verifying. */
@RunWith(RobolectricTestRunner::class)
class ComposerScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun skipUnlessRoborazziActive() = assumeTrue(roborazziSystemPropertyTaskType().isEnabled())

    @Before
    fun installSampleArtwork() = SampleArtworkCoil.install(ApplicationProvider.getApplicationContext())

    @After
    fun uninstallSampleArtwork() = SampleArtworkCoil.uninstall()

    private val sampleAlbums = SampleLibrary.albums.take(3)

    private fun recording(albumIndex: Int, year: Int): OtherRecording {
        val album = sampleAlbums[albumIndex]
        val song = SampleLibrary.songs.first { it.albumId == album.id }.toSong().copy(date = LocalDate(year, 1, 1))
        return OtherRecording(song.albumGroupKey, song.album, song.albumArtist, listOf(song))
    }

    private fun composer(name: String, works: List<ComposerWork>, albumIndexes: List<Int>): ComposerUiState {
        val albums = albumIndexes.map { sampleAlbums[it].toAlbum() }
        return ComposerUiState(
            loading = false,
            composer = ComposerWorks(name, works, albums.mapNotNull { it.groupKey }),
            albums = albums,
        )
    }

    private val worksOfFirst = listOf(
        ComposerWork("Piano Sonata No. 3", "Op. 2 No. 3", listOf(recording(0, 2015), recording(1, 2003), recording(2, 1988))),
        ComposerWork("String Quartet No. 7", "Op. 59 No. 1", listOf(recording(1, 2003))),
        ComposerWork("Symphony No. 5 in C minor", "Op. 67", listOf(recording(0, 2015), recording(2, 1988))),
        ComposerWork("Violin Concerto in D", "Op. 61", listOf(recording(2, 1988))),
    )

    private val worksOfSecond = listOf(
        ComposerWork("Symphony No. 4 in E minor", "Op. 98", listOf(recording(0, 2015), recording(1, 2003))),
        ComposerWork("Variations on a Theme", null, listOf(recording(1, 2003))),
    )

    @Test
    fun composerPage() = capture("composer-page.png", composer("Ludwig van Beethoven", worksOfFirst, listOf(0, 1, 2)))

    @Test
    fun composerPageSecond() = capture("composer-page-second.png", composer("Johannes Brahms", worksOfSecond, listOf(0, 1)))

    private fun capture(fileName: String, state: ComposerUiState) {
        composeTestRule.setContent {
            S2Theme {
                Surface {
                    ComposerDetailScreen(uiState = state, onNavigateUp = {}, onOpenAlbum = {}, onAlbumMore = {})
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = File(shotsDir, fileName).path,
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
