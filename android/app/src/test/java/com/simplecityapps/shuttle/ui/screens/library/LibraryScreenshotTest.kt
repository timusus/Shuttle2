package com.simplecityapps.shuttle.ui.screens.library

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.simplecityapps.shuttle.fixtures.SampleLibrary
import com.simplecityapps.shuttle.persistence.LibraryTab
import com.simplecityapps.shuttle.ui.DocsDesignRoborazziOptions
import com.simplecityapps.shuttle.ui.SampleArtworkCoil
import com.simplecityapps.shuttle.ui.preview.sampleSongs
import com.simplecityapps.shuttle.ui.preview.toAlbum
import com.simplecityapps.shuttle.ui.preview.toAlbumArtist
import com.simplecityapps.shuttle.ui.preview.toGenre
import com.simplecityapps.shuttle.ui.preview.toPlaylist
import com.simplecityapps.shuttle.ui.preview.toSong
import com.simplecityapps.shuttle.ui.sampleSeed
import com.simplecityapps.shuttle.ui.screens.library.albums.readyAlbumList
import com.simplecityapps.shuttle.ui.screens.library.playlists.readyPlaylistList
import com.simplecityapps.shuttle.ui.screens.library.songs.readySongList
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Records the Compose library container and its detail screens into `docs/design/library/` for review (#377),
 * showing the sample library with its generated covers ([SampleArtworkCoil]).
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

    private val albums = SampleLibrary.albums.map { it.toAlbum() }

    private val songs = SampleLibrary.songs.map { it.toSong() }

    @Before
    fun installSampleArtwork() = SampleArtworkCoil.install(ApplicationProvider.getApplicationContext())

    @After
    fun uninstallSampleArtwork() = SampleArtworkCoil.uninstall()

    private fun shot(name: String) {
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = File(shotsDir, "$name.png").path,
            roborazziOptions = DocsDesignRoborazziOptions,
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
    fun phonePlaylists() {
        val playlists = SampleLibrary.playlists.mapIndexed { index, playlist -> playlist.toPlaylist(id = index + 1L) }
        library.setContent(
            libraryState(currentTab = LibraryTab.Playlists),
            chromeWithMenu(subtitle = "${playlists.size} playlists"),
            LibraryPageStates(playlists = readyPlaylistList(playlists, smartPlaylists = SmartPlaylistId.entries.map { it.smartPlaylist })),
        )
        shot("phone-playlists")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phoneAlbumDetail() {
        val album = SampleLibrary.album("phase-garden")
        val tracks = album.songs.map { it.toSong() }
        val albumSongs = tracks.take(3) + tracks.drop(3).map { it.copy(disc = 2) }
        detail.setAlbum(readyAlbumDetail(album = album.toAlbum(), songs = albumSongs, currentSong = albumSongs[1]))
        shot("phone-album-detail")
    }

    /** Colour from artwork on: the cover's seed tints the whole screen (#496). */
    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phoneAlbumDetailArtworkTint() {
        val album = SampleLibrary.album("phase-garden")
        val tracks = album.songs.map { it.toSong() }
        detail.setAlbum(readyAlbumDetail(album = album.toAlbum(), songs = tracks, currentSong = tracks[1]).copy(seed = sampleSeed(album.title)))
        shot("phone-album-detail-artwork-tint")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phoneArtistDetail() {
        val artist = SampleLibrary.artist("Juniper Static")
        val artistSongs = artist.albums.flatMap { album -> album.songs.map { it.toSong() } }
        val artistAlbums = artist.albums.map { album -> albumOf(album.songs.map { it.toSong() }, year = album.year) }
        detail.setAlbumArtist(
            readyAlbumArtistDetail(
                artist = artist.toAlbumArtist(),
                songs = artistSongs,
                albums = artistAlbums,
                expandedAlbums = setOfNotNull(artistAlbums.first().groupKey),
            ),
        )
        shot("phone-artist-detail")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phoneArtistDetailArtworkTint() {
        val artist = SampleLibrary.artist("Juniper Static")
        val artistSongs = artist.albums.flatMap { album -> album.songs.map { it.toSong() } }
        val artistAlbums = artist.albums.map { album -> albumOf(album.songs.map { it.toSong() }, year = album.year) }
        detail.setAlbumArtist(
            readyAlbumArtistDetail(artist = artist.toAlbumArtist(), songs = artistSongs, albums = artistAlbums)
                .copy(seed = sampleSeed(artist.albums.maxBy { it.year }.title)),
        )
        shot("phone-artist-detail-artwork-tint")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phoneGenreDetail() {
        val genre = SampleLibrary.genres.first { it.name == "Electronic" }
        detail.setGenre(
            readyGenreDetail(
                genre = genre.toGenre(),
                albums = SampleLibrary.albums.filter { it.genre == genre.name }.map { it.toAlbum() },
                songs = genre.songs.take(6).map { it.toSong() },
            ),
        )
        shot("phone-genre-detail")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phonePlaylistDetail() {
        detail.setPlaylist(roadTrip())
        shot("phone-playlist-detail")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phonePlaylistSelection() {
        detail.setPlaylist(roadTrip(selectedIds = setOf(10L, 12L)))
        shot("phone-playlist-selection")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phoneSmartPlaylistDetail() {
        detail.setSmartPlaylist(readySmartPlaylistDetail(smartPlaylist = SmartPlaylistId.History.smartPlaylist, songs = sampleSongs(8)))
        shot("phone-smart-playlist-detail")
    }

    private fun roadTrip(selectedIds: Set<Long> = emptySet()): PlaylistDetailUiState {
        val playlist = SampleLibrary.playlist("Road Trip")
        return readyPlaylistDetail(
            playlist = playlist.toPlaylist(id = 7),
            songs = playlistEntries(playlist.songs.map { it.toSong() }),
            selectedIds = selectedIds,
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
