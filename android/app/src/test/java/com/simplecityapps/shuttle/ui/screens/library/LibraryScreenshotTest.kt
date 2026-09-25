package com.simplecityapps.shuttle.ui.screens.library

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createSong
import com.simplecityapps.shuttle.persistence.LibraryTab
import com.simplecityapps.shuttle.ui.screens.library.albums.readyAlbumList
import com.simplecityapps.shuttle.ui.screens.library.songs.readySongList
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Records the Compose library container and its detail screens into `docs/design/library/` for review (#377).
 * A no-op under plain `testDebugUnitTest`; record with
 * `./gradlew :android:app:recordRoborazziDebug --tests '*LibraryScreenshotTest*'`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val library = LibraryScreenRobot(composeTestRule)
    private val detail = LibraryDetailRobot(composeTestRule)

    private val artists = listOf("Radiohead", "Portishead", "Massive Attack", "Björk")

    private val albums = listOf("OK Computer", "Dummy", "Mezzanine", "Homogenic", "Kid A", "Third", "Protection", "Post")
        .mapIndexed { index, name -> createAlbum(name = name, albumArtist = artists[index % artists.size], songCount = 10 + index, year = 1994 + index) }

    private val songs = albums.flatMapIndexed { albumIndex, album ->
        (1..3).map { track -> createSong(id = albumIndex * 10L + track, name = "${album.name} $track", albumArtist = album.albumArtist.orEmpty(), album = album.name.orEmpty(), track = track, duration = 200_000 + track * 17_000) }
    }

    private fun shot(name: String) {
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = File(shotsDir, "$name.png").path,
            roborazziOptions = RoborazziOptions(captureType = RoborazziOptions.CaptureType.Screenshot()),
        )
    }

    private fun container(tab: LibraryTab, subtitle: String, albumViewMode: ViewMode = ViewMode.List) {
        library.setContent(
            libraryState(currentTab = tab),
            chromeWithMenu(subtitle = subtitle),
            LibraryPageStates(songs = readySongList(songs = songs), albums = readyAlbumList(albums = albums, viewMode = albumViewMode)),
        )
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phoneSongs() {
        container(LibraryTab.Songs, "${songs.size} songs")
        shot("phone-songs")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phoneAlbums() {
        container(LibraryTab.Albums, "${albums.size} albums")
        shot("phone-albums")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun tabletAlbums() {
        container(LibraryTab.Albums, "${albums.size} albums", albumViewMode = ViewMode.Grid)
        shot("tablet-albums")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun tabletSongs() {
        container(LibraryTab.Songs, "${songs.size} songs")
        shot("tablet-songs")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phoneAlbumDetail() {
        val albumSongs = okComputerSongs(disc = 1) + okComputerSongs(disc = 2)
        detail.setAlbum(readyAlbumDetail(songs = albumSongs, currentSong = albumSongs[1]))
        shot("phone-album-detail")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phoneArtistDetail() {
        val artistSongs = songs.filter { it.albumArtist == "Radiohead" }
        val artistAlbums = artistSongs.groupBy { it.album }.values.map { albumOf(it) }
        detail.setAlbumArtist(
            readyAlbumArtistDetail(
                artist = createAlbumArtist(name = "Radiohead"),
                songs = artistSongs,
                albums = artistAlbums,
                expandedAlbums = setOfNotNull(artistAlbums.first().groupKey),
            ),
        )
        shot("phone-artist-detail")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phoneGenreDetail() {
        detail.setGenre(readyGenreDetail(albums = albums.take(4), songs = songs.take(6)))
        shot("phone-genre-detail")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phonePlaylistDetail() {
        detail.setPlaylist(readyPlaylistDetail(songs = playlistEntries(songs.take(8))))
        shot("phone-playlist-detail")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phonePlaylistSelection() {
        detail.setPlaylist(readyPlaylistDetail(songs = playlistEntries(songs.take(8)), selectedIds = setOf(10L, 12L)))
        shot("phone-playlist-selection")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phoneSmartPlaylistDetail() {
        detail.setSmartPlaylist(readySmartPlaylistDetail(songs = songs.take(8)))
        shot("phone-smart-playlist-detail")
    }

    private companion object {
        val shotsDir: File by lazy {
            generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .first { File(it, "gradlew").exists() && File(it, "docs/design").isDirectory }
                .resolve("docs/design/library")
        }
    }
}
