package com.simplecityapps.shuttle.ui.screens.library

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyTaskType
import com.simplecityapps.shuttle.fixtures.SampleLibrary
import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.persistence.LibraryTab
import com.simplecityapps.shuttle.sorting.ArtistSongSortOrder
import com.simplecityapps.shuttle.ui.DocsDesignRoborazziOptions
import com.simplecityapps.shuttle.ui.SampleArtworkCoil
import com.simplecityapps.shuttle.ui.preview.sampleSongs
import com.simplecityapps.shuttle.ui.preview.toAlbum
import com.simplecityapps.shuttle.ui.preview.toAlbumArtist
import com.simplecityapps.shuttle.ui.preview.toGenre
import com.simplecityapps.shuttle.ui.preview.toPlaylist
import com.simplecityapps.shuttle.ui.preview.toSong
import com.simplecityapps.shuttle.ui.screens.library.albumartists.readyAlbumArtistList
import com.simplecityapps.shuttle.ui.screens.library.albums.readyAlbumList
import com.simplecityapps.shuttle.ui.screens.library.genres.readyGenreList
import com.simplecityapps.shuttle.ui.screens.library.playlists.readyPlaylistList
import com.simplecityapps.shuttle.ui.screens.library.songs.readySongList
import java.io.File
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Records the Compose library container and its detail screens into `docs/design/library/` for review (#377),
 * showing the sample library with its generated covers ([SampleArtworkCoil]).
 * A no-op under plain `testDebugUnitTest`; record with
 * `./gradlew :android:app:recordRoborazziDebug --tests '*LibraryScreenshotTest*'`.
 */
@RunWith(RobolectricTestRunner::class)
class LibraryScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val library = LibraryScreenRobot(composeTestRule)
    private val detail = LibraryDetailRobot(composeTestRule)

    private val albums = SampleLibrary.albums.map { it.toAlbum() }

    private val songs = SampleLibrary.songs.map { it.toSong() }

    // Skip the whole render (#538) unless Roborazzi is recording or verifying;
    // verifyRoborazziDebug still renders every board on every landing.
    @Before
    fun skipUnlessRoborazziActive() = assumeTrue(roborazziSystemPropertyTaskType().isEnabled())

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

    private fun container(tab: LibraryTab, count: String, albumViewMode: ViewMode = ViewMode.Grid) {
        library.setContent(
            libraryState(currentTab = tab),
            when (tab) {
                LibraryTab.Albums -> libraryControls(count, sorts("Album Name", "Artist Name", "Year"), albumViewMode, onPlay = {}, onShuffle = {})
                else -> libraryControls(count, sorts("Album Name", "Song Name", "Artist Name"), onPlay = {}, onShuffle = {})
            },
            LibraryPageStates(songs = readySongList(songs = songs), albums = readyAlbumList(albums = albums, viewMode = albumViewMode)),
        )
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phoneSongs() {
        container(LibraryTab.Songs, "${songs.size} songs")
        shot("phone-songs")
    }

    /** The Songs page at 200% text: the controls row grows and keeps Shuffle and Play on screen (#730). */
    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phoneSongsLargeText() {
        library.setContent(
            libraryState(currentTab = LibraryTab.Songs),
            libraryControls("${songs.size} songs", sorts("Album Name", "Song Name", "Artist Name"), onPlay = {}, onShuffle = {}),
            LibraryPageStates(songs = readySongList(songs = songs)),
            fontScale = 2f,
        )
        shot("phone-songs-large-text")
    }

    /** Artists carries the same control row as Songs: count, sort, view, Shuffle and Play. */
    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phoneArtists() {
        val artists = SampleLibrary.artists.map { it.toAlbumArtist() }
        library.setContent(
            libraryState(currentTab = LibraryTab.Artists),
            libraryControls("${artists.size} artists", sorts("Artist Name", "Album Count"), ViewMode.List, onPlay = {}, onShuffle = {}),
            LibraryPageStates(artists = readyAlbumArtistList(artists)),
        )
        shot("phone-artists")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phoneAlbums() {
        container(LibraryTab.Albums, "${albums.size} albums")
        shot("phone-albums")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phoneAlbumsList() {
        container(LibraryTab.Albums, "${albums.size} albums", albumViewMode = ViewMode.List)
        shot("phone-albums-list")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun tabletAlbums() {
        container(LibraryTab.Albums, "${albums.size} albums")
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
            libraryControls("${playlists.size} playlists", sorts("Name", "Date Created")),
            LibraryPageStates(
                playlists = readyPlaylistList(
                    playlists,
                    smartPlaylists = SmartPlaylistId.entries.map { it.smartPlaylist },
                    covers = SampleLibrary.playlists.mapIndexed { index, playlist ->
                        index + 1L to playlist.songs.map { it.toSong() }.distinctBy { it.albumGroupKey }.take(4)
                    }.toMap(),
                ),
            ),
        )
        shot("phone-playlists")
    }

    /** Each genre's tile is a 2x2 patchwork of its albums' covers (#135). */
    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phoneGenres() {
        val genres = SampleLibrary.genres.map { it.toGenre() }
        library.setContent(
            libraryState(currentTab = LibraryTab.Genres),
            libraryControls("${genres.size} genres", sorts("Genre Name", "Song Count"), ViewMode.List, onPlay = {}, onShuffle = {}),
            LibraryPageStates(
                genres = readyGenreList(genres),
                // The sample genres hold one or two albums; Indie Rock borrows three so the shot shows every mosaic layout.
                genreCovers = SampleLibrary.genres.associate { genre ->
                    val songs = if (genre.name == "Indie Rock") SampleLibrary.songs else genre.songs
                    genre.name to songs.map { it.toSong() }.distinctBy { it.albumGroupKey }.take(if (genre.name == "Indie Rock") 3 else 4)
                },
            ),
        )
        shot("phone-genres")
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

    /** Juniper Static's songs listed flat by title, under the Albums section they bring back (#678). */
    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phoneArtistDetailFlat() {
        val artist = SampleLibrary.artist("Juniper Static")
        val artistSongs = artist.albums.flatMap { album -> album.songs.map { it.toSong() } }
        val artistAlbums = artist.albums.map { album -> albumOf(album.songs.map { it.toSong() }, year = album.year) }
        detail.setAlbumArtist(
            readyAlbumArtistDetail(
                artist = artist.toAlbumArtist(),
                songs = artistSongs.sortedBy { it.name },
                albums = artistAlbums,
                sortOrder = ArtistSongSortOrder.SongTitle,
            ),
        )
        shot("phone-artist-detail-flat")
    }

    /** Juniper Static's albums, folded, then Appears On: the compilation carrying their live edit (#637). */
    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phoneArtistDetailAppearsOn() {
        val artist = SampleLibrary.artist("Juniper Static")
        val artistSongs = artist.albums.flatMap { album -> album.songs.map { it.toSong() } }
        val artistAlbums = artist.albums.map { album -> albumOf(album.songs.map { it.toSong() }, year = album.year) }
        val compilation = SampleLibrary.compilation
        val credited = compilation.songs.filter { it.artist == artist.name }.map { it.toSong() }
        detail.setAlbumArtist(
            readyAlbumArtistDetail(
                artist = artist.toAlbumArtist(),
                songs = artistSongs + credited,
                albums = artistAlbums,
                appearsOn = listOf(albumOf(compilation.songs.map { it.toSong() }, year = compilation.year)),
            ),
        )
        shot("phone-artist-detail-appears-on")
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
