package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createSong
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.manySongs
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlbumArtistDetailTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = AlbumArtistDetailRobot(composeTestRule)

    // region View state rendering

    @Test
    fun `loading state shows loading indicator`() {
        robot.setContent(loadingAlbumArtistDetail)
        robot.assertTextDisplayed("Loading\u2026")
    }

    @Test
    fun `empty state shows empty message`() {
        robot.setContent(emptyAlbumArtistDetail())
        robot.assertTextDisplayed("No songs")
    }

    @Test
    fun `ready state shows album name`() {
        robot.setContent(
            readyAlbumArtistDetail(
                albums = listOf(createAlbum(name = "Abbey Road")),
            )
        )
        robot.assertTextDisplayed("Abbey Road")
    }

    @Test
    fun `ready state shows album year and song count`() {
        robot.setContent(
            readyAlbumArtistDetail(
                albums = listOf(createAlbum(name = "Abbey Road", year = 1969, songCount = 17)),
            )
        )
        robot.assertSubtextDisplayed("1969")
        robot.assertSubtextDisplayed("17 songs")
    }

    @Test
    fun `ready state shows multiple albums`() {
        robot.setContent(
            readyAlbumArtistDetail(
                albums = listOf(
                    createAlbum(name = "Abbey Road"),
                    createAlbum(name = "Let It Be"),
                ),
            )
        )
        robot.assertTextDisplayed("Abbey Road")
        robot.assertTextDisplayed("Let It Be")
    }

    @Test
    fun `ready state shows section headers`() {
        robot.setContent(
            readyAlbumArtistDetail(
                albums = listOf(createAlbum(name = "Abbey Road")),
                songs = listOf(createSong(name = "Come Together")),
            )
        )
        robot.assertTextDisplayed("Albums")
        robot.assertTextDisplayed("Songs")
    }

    @Test
    fun `ready state shows song name`() {
        robot.setContent(
            readyAlbumArtistDetail(
                songs = listOf(createSong(name = "Come Together")),
            )
        )
        robot.assertTextDisplayed("Come Together")
    }

    @Test
    fun `ready state shows multiple songs`() {
        robot.setContent(
            readyAlbumArtistDetail(
                songs = listOf(
                    createSong(id = 1, name = "Come Together"),
                    createSong(id = 2, name = "Something"),
                    createSong(id = 3, name = "Here Comes the Sun"),
                ),
            )
        )
        robot.assertTextDisplayed("Come Together")
        robot.assertTextDisplayed("Something")
        robot.assertTextDisplayed("Here Comes the Sun")
    }

    @Test
    fun `current song is highlighted`() {
        val song = createSong(id = 1, name = "Now Playing Song")
        robot.setContent(
            readyAlbumArtistDetail(
                songs = listOf(song),
                currentSong = song,
            )
        )
        robot.assertCurrentSongHighlighted("Now Playing Song")
    }

    @Test
    fun `non-current song is not highlighted`() {
        robot.setContent(
            readyAlbumArtistDetail(
                songs = listOf(createSong(id = 1, name = "Some Song")),
                currentSong = null,
            )
        )
        robot.assertCurrentSongNotHighlighted()
    }

    @Test
    fun `albums section hidden when no albums`() {
        robot.setContent(
            readyAlbumArtistDetail(
                albums = emptyList(),
                songs = listOf(createSong(name = "Solo Song")),
            )
        )
        robot.assertTextNotDisplayed("Albums")
        robot.assertTextDisplayed("Songs")
    }

    @Test
    fun `songs section hidden when no songs`() {
        robot.setContent(
            readyAlbumArtistDetail(
                albums = listOf(createAlbum(name = "My Album")),
                songs = emptyList(),
            )
        )
        robot.assertTextDisplayed("Albums")
        robot.assertTextNotDisplayed("Songs")
    }

    // endregion

    // region Album callbacks

    @Test
    fun `album click invokes onAlbumClick`() {
        val album = createAlbum(name = "Click Album")
        robot.setContent(readyAlbumArtistDetail(albums = listOf(album)))
        robot.clickText("Click Album")
        robot.lastAlbumClicked shouldBe album
    }

    @Test
    fun `album artwork click invokes onOpenAlbum`() {
        val album = createAlbum(name = "Open Album")
        robot.setContent(readyAlbumArtistDetail(albums = listOf(album)))
        robot.clickAlbumArtwork()
        robot.lastAlbumOpened shouldBe album
    }

    // endregion

    // region Album expansion

    @Test
    fun `collapsed album does not show its tracks`() {
        val songs = listOf(createSong(id = 1, name = "Come Together", album = "Abbey Road"))
        robot.setContent(
            readyAlbumArtistDetail(albums = listOf(albumFor(songs)), songs = songs)
        )
        robot.assertAlbumTrackNotDisplayed("Come Together")
    }

    @Test
    fun `expanded album shows its tracks`() {
        val songs = listOf(
            createSong(id = 1, name = "Come Together", album = "Abbey Road"),
            createSong(id = 2, name = "Something", album = "Abbey Road"),
        )
        val album = albumFor(songs)
        robot.setContent(
            readyAlbumArtistDetail(
                albums = listOf(album),
                songs = songs,
                expandedAlbums = setOfNotNull(album.groupKey),
            )
        )
        robot.assertAlbumTrackDisplayed("Come Together")
        robot.assertAlbumTrackDisplayed("Something")
    }

    @Test
    fun `expanded album only shows its own tracks`() {
        val abbeyRoadSongs = listOf(createSong(id = 1, name = "Come Together", album = "Abbey Road"))
        val letItBeSongs = listOf(createSong(id = 2, name = "Get Back", album = "Let It Be"))
        val abbeyRoad = albumFor(abbeyRoadSongs)
        robot.setContent(
            readyAlbumArtistDetail(
                albums = listOf(abbeyRoad, albumFor(letItBeSongs)),
                songs = abbeyRoadSongs + letItBeSongs,
                expandedAlbums = setOfNotNull(abbeyRoad.groupKey),
            )
        )
        robot.assertAlbumTrackDisplayed("Come Together")
        robot.assertAlbumTrackNotDisplayed("Get Back")
    }

    @Test
    fun `album row click invokes onAlbumClick to toggle expansion`() {
        val songs = listOf(createSong(id = 1, name = "Come Together", album = "Abbey Road"))
        val album = albumFor(songs)
        robot.setContent(readyAlbumArtistDetail(albums = listOf(album), songs = songs))
        robot.clickText("Abbey Road")
        robot.lastAlbumClicked shouldBe album
    }

    @Test
    fun `track inside an expanded album plays that album's songs`() {
        val abbeyRoadSongs = listOf(
            createSong(id = 1, name = "Come Together", album = "Abbey Road"),
            createSong(id = 2, name = "Something", album = "Abbey Road"),
        )
        val letItBeSongs = listOf(createSong(id = 3, name = "Get Back", album = "Let It Be"))
        val abbeyRoad = albumFor(abbeyRoadSongs)
        robot.setContent(
            readyAlbumArtistDetail(
                albums = listOf(abbeyRoad, albumFor(letItBeSongs)),
                songs = abbeyRoadSongs + letItBeSongs,
                expandedAlbums = setOfNotNull(abbeyRoad.groupKey),
            )
        )
        robot.clickAlbumTrack("Something")
        robot.lastAlbumSongClicked shouldBe (abbeyRoadSongs[1] to abbeyRoadSongs)
    }

    // endregion

    // region Song callbacks

    @Test
    fun `song click invokes onSongClick`() {
        val song = createSong(name = "Click Song")
        robot.setContent(readyAlbumArtistDetail(songs = listOf(song)))
        robot.clickText("Click Song")
        robot.lastSongClicked shouldBe song
    }

    // endregion

    // region Song context menu

    @Test
    fun `song context menu shows standard items for local song`() {
        robot.setContent(
            readyAlbumArtistDetail(
                songs = listOf(createSong(mediaProvider = MediaProviderType.Shuttle)),
            )
        )
        robot.openSongContextMenu()

        robot.assertTextDisplayed("Add to Queue")
        robot.assertTextDisplayed("Add to Playlist")
        robot.assertTextDisplayed("Play Next")
        robot.assertTextDisplayed("Song Info")
        robot.assertTextDisplayed("Exclude")
        robot.assertTextDisplayed("Edit Tags")
        robot.assertTextDisplayed("Delete")
    }

    @Test
    fun `song context menu hides Edit Tags for non-tag-editing provider`() {
        robot.setContent(
            readyAlbumArtistDetail(
                songs = listOf(createSong(mediaProvider = MediaProviderType.Jellyfin)),
            )
        )
        robot.openSongContextMenu()
        robot.assertTextNotDisplayed("Edit Tags")
    }

    @Test
    fun `song context menu invokes onAddToQueue`() {
        val song = createSong(name = "Queue Me")
        robot.setContent(readyAlbumArtistDetail(songs = listOf(song)))
        robot.openSongContextMenu()
        robot.clickMenuItem("Add to Queue")
        robot.lastAddedToQueue shouldBe song
    }

    @Test
    fun `song context menu invokes onPlayNext`() {
        val song = createSong(name = "Next Song")
        robot.setContent(readyAlbumArtistDetail(songs = listOf(song)))
        robot.openSongContextMenu()
        robot.clickMenuItem("Play Next")
        robot.lastPlayNext shouldBe song
    }

    @Test
    fun `song context menu invokes onSongInfo`() {
        val song = createSong(name = "Info Song")
        robot.setContent(readyAlbumArtistDetail(songs = listOf(song)))
        robot.openSongContextMenu()
        robot.clickMenuItem("Song Info")
        robot.lastSongInfo shouldBe song
    }

    @Test
    fun `song context menu invokes onExclude`() {
        val song = createSong(name = "Exclude Me")
        robot.setContent(readyAlbumArtistDetail(songs = listOf(song)))
        robot.openSongContextMenu()
        robot.clickMenuItem("Exclude")
        robot.lastExcluded shouldBe song
    }

    @Test
    fun `song context menu invokes onEditTags for Shuttle provider`() {
        val song = createSong(name = "Tag Me", mediaProvider = MediaProviderType.Shuttle)
        robot.setContent(readyAlbumArtistDetail(songs = listOf(song)))
        robot.openSongContextMenu()
        robot.clickMenuItem("Edit Tags")
        robot.lastEditTags shouldBe song
    }

    @Test
    fun `song context menu invokes onDelete for deletable song`() {
        val song = createSong(name = "Delete Me", mediaProvider = MediaProviderType.Shuttle)
        robot.setContent(readyAlbumArtistDetail(songs = listOf(song)))
        robot.openSongContextMenu()
        robot.clickMenuItem("Delete")
        robot.lastDeleted shouldBe song
    }

    // endregion

    // region Album context menu

    @Test
    fun `album context menu shows standard items`() {
        robot.setContent(
            readyAlbumArtistDetail(
                albums = listOf(createAlbum(mediaProviders = listOf(MediaProviderType.Shuttle))),
            )
        )
        robot.openAlbumContextMenu()

        robot.assertTextDisplayed("View Album")
        robot.assertTextDisplayed("Play")
        robot.assertTextDisplayed("Add to Queue")
        robot.assertTextDisplayed("Add to Playlist")
        robot.assertTextDisplayed("Play Next")
        robot.assertTextDisplayed("Exclude")
        robot.assertTextDisplayed("Edit Tags")
    }

    @Test
    fun `album context menu hides Edit Tags for non-tag-editing provider`() {
        robot.setContent(
            readyAlbumArtistDetail(
                albums = listOf(createAlbum(mediaProviders = listOf(MediaProviderType.Jellyfin))),
            )
        )
        robot.openAlbumContextMenu()
        robot.assertTextNotDisplayed("Edit Tags")
    }

    @Test
    fun `album context menu invokes onOpenAlbum for View Album`() {
        val album = createAlbum(name = "View Album Target")
        robot.setContent(readyAlbumArtistDetail(albums = listOf(album)))
        robot.openAlbumContextMenu()
        robot.clickMenuItem("View Album")
        robot.lastAlbumOpened shouldBe album
    }

    @Test
    fun `album context menu invokes onAlbumPlay`() {
        val album = createAlbum(name = "Play Album")
        robot.setContent(readyAlbumArtistDetail(albums = listOf(album)))
        robot.openAlbumContextMenu()
        robot.clickMenuItem("Play")
        robot.lastAlbumPlay shouldBe album
    }

    @Test
    fun `album context menu invokes onAlbumAddToQueue`() {
        val album = createAlbum(name = "Queue Album")
        robot.setContent(readyAlbumArtistDetail(albums = listOf(album)))
        robot.openAlbumContextMenu()
        robot.clickMenuItem("Add to Queue")
        robot.lastAlbumAddedToQueue shouldBe album
    }

    @Test
    fun `album context menu invokes onAlbumPlayNext`() {
        val album = createAlbum(name = "Next Album")
        robot.setContent(readyAlbumArtistDetail(albums = listOf(album)))
        robot.openAlbumContextMenu()
        robot.clickMenuItem("Play Next")
        robot.lastAlbumPlayNext shouldBe album
    }

    @Test
    fun `album context menu invokes onAlbumExclude`() {
        val album = createAlbum(name = "Exclude Album")
        robot.setContent(readyAlbumArtistDetail(albums = listOf(album)))
        robot.openAlbumContextMenu()
        robot.clickMenuItem("Exclude")
        robot.lastAlbumExcluded shouldBe album
    }

    @Test
    fun `album context menu invokes onAlbumEditTags for Shuttle provider`() {
        val album = createAlbum(name = "Tag Album", mediaProviders = listOf(MediaProviderType.Shuttle))
        robot.setContent(readyAlbumArtistDetail(albums = listOf(album)))
        robot.openAlbumContextMenu()
        robot.clickMenuItem("Edit Tags")
        robot.lastAlbumEditTags shouldBe album
    }

    // endregion

    // region Top bar title

    @Test
    fun `top bar does not show artist name while the header is visible`() {
        robot.setContent(readyAlbumArtistDetail(albumArtist = createAlbumArtist(name = "The Beatles"), songs = manySongs()))
        robot.assertTopBarTitleNotDisplayed("The Beatles")
    }

    @Test
    fun `top bar shows artist name and subtitle once the header scrolls away`() {
        robot.setContent(
            readyAlbumArtistDetail(
                albumArtist = createAlbumArtist(name = "The Beatles", albumCount = 2, songCount = 30),
                songs = manySongs(),
            )
        )
        robot.scrollPastHeader()
        robot.assertTopBarTitleDisplayed("The Beatles")
        robot.assertTopBarTitleDisplayed("2 albums · 30 songs")
    }

    // endregion
}
