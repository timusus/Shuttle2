package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createSong
import com.simplecityapps.shuttle.sorting.ArtistSongSortOrder
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AlbumArtistDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = LibraryDetailRobot(composeTestRule)

    @Test
    fun `grouped by album, the albums head the song list and there is no separate albums shelf`() {
        robot.setAlbumArtist(readyAlbumArtistDetail())

        robot.assertTextDisplayed("Juniper Static")
        robot.assertTextDisplayed("1 album · 3 songs")
        robot.assertTextShownTimes("Albums", 1)
        robot.assertTextNotDisplayed("Songs")
        robot.assertTextShownTimes("Phase Garden", 1)
    }

    @Test
    fun `listed flat, the albums shelf comes first, then every song`() {
        robot.setAlbumArtist(readyAlbumArtistDetail(sortOrder = ArtistSongSortOrder.SongTitle))

        robot.assertTextDisplayed("Albums")
        robot.assertTextDisplayed("Phase Garden")
        robot.scrollTo("Songs")
        robot.assertTextDisplayed("Songs")
        robot.assertTextShownTimes("Soft Machines at Dawn", 1)
    }

    @Test
    fun `listed flat, a tap on an album in the shelf opens it`() {
        val songs = phaseGardenSongs()
        val album = albumOf(songs)
        robot.setAlbumArtist(readyAlbumArtistDetail(songs = songs, albums = listOf(album), sortOrder = ArtistSongSortOrder.SongTitle))

        robot.clickText("Phase Garden")

        robot.lastAlbumClicked shouldBe album
        robot.lastAlbumToggled shouldBe null
    }

    @Test
    fun `the songs header's menu picks the sort, labelled with the current one`() {
        robot.setAlbumArtist(readyAlbumArtistDetail(sortOrder = ArtistSongSortOrder.AlbumOldest))

        robot.assertContentDescriptionShown("Sort: Album, oldest first")
        robot.openArtistSortMenu()
        robot.clickMenuItem("Most played")

        robot.lastArtistSortOrder shouldBe ArtistSongSortOrder.MostPlayed
    }

    @Test
    fun `Expand all unfolds the albums`() {
        robot.setAlbumArtist(readyAlbumArtistDetail())

        robot.clickContentDescription("Expand all")

        robot.expandedAll shouldBe true
    }

    @Test
    fun `once every album is unfolded, Collapse all folds them`() {
        val album = albumOf(phaseGardenSongs())
        robot.setAlbumArtist(readyAlbumArtistDetail(albums = listOf(album), expandedAlbums = setOfNotNull(album.groupKey)))
        robot.assertContentDescriptionShown("Expand all", shown = false)
        robot.clickContentDescription("Collapse all")
        robot.collapsedAll shouldBe true
    }

    @Test
    fun `listed flat, there are no albums to fold`() {
        robot.setAlbumArtist(readyAlbumArtistDetail(sortOrder = ArtistSongSortOrder.SongTitle))

        robot.assertContentDescriptionShown("Expand all", shown = false)
        robot.assertContentDescriptionShown("Collapse all", shown = false)
    }

    @Test
    fun `an unfolded album's header pins under the bar while its songs scroll by`() {
        val songs = (1..40).map { createSong(id = 100L + it, name = "Long Track $it", albumArtist = "Juniper Static", album = "Endless Loop", track = it) }
        val album = albumOf(songs)
        robot.setAlbumArtist(readyAlbumArtistDetail(songs = songs, albums = listOf(album), expandedAlbums = setOfNotNull(album.groupKey)))
        robot.assertPinnedAlbum(null)

        robot.scrollTo("Long Track 40")

        robot.assertPinnedAlbum("Endless Loop")
    }

    @Test
    fun `tapping an album's row folds it and tapping its thumbnail opens it`() {
        val nightSignals = listOf(createSong(id = 50, name = "Tape Hiss", albumArtist = "Juniper Static", album = "Night Signals", track = 1))
        val albums = listOf(albumOf(phaseGardenSongs()), albumOf(nightSignals))
        val state = readyAlbumArtistDetail(songs = phaseGardenSongs() + nightSignals, albums = albums)
        robot.setAlbumArtist(state)

        robot.clickText("Phase Garden")
        robot.lastAlbumToggled shouldBe state.albums[0]
        robot.lastAlbumClicked shouldBe null

        robot.clickOpenAlbum("Night Signals")
        robot.lastAlbumClicked shouldBe state.albums[1]
        robot.lastAlbumToggled shouldBe state.albums[0] // the thumbnail opens the album, it doesn't fold it
    }

    @Test
    fun `a folded album hides its songs`() {
        robot.setAlbumArtist(readyAlbumArtistDetail())

        robot.assertTextShownTimes("Soft Machines at Dawn", 0)
    }

    @Test
    fun `an unfolded album lists its songs, which play on through the rest of the artist's songs`() {
        val phaseGarden = phaseGardenSongs()
        val tapeHiss = listOf(createSong(id = 50, name = "Tape Hiss", albumArtist = "Juniper Static", album = "Night Signals", track = 1))
        val albums = listOf(albumOf(tapeHiss), albumOf(phaseGarden))
        val state = readyAlbumArtistDetail(songs = phaseGarden + tapeHiss, albums = albums, expandedAlbums = albums.mapNotNullTo(HashSet()) { it.groupKey })
        robot.setAlbumArtist(state)

        robot.assertTextShownTimes("Soft Machines at Dawn", 1)
        robot.clickText("Soft Machines at Dawn")

        robot.lastPlayed shouldBe (tapeHiss + phaseGarden to 2)
    }

    @Test
    fun `songs on none of their albums trail the album sections as Other Songs`() {
        val stray = createSong(id = 60, name = "Loose Thread", albumArtist = "Juniper Static", album = "Loose Tracks")
        robot.setAlbumArtist(readyAlbumArtistDetail(songs = phaseGardenSongs() + stray, albums = listOf(albumOf(phaseGardenSongs()))))

        robot.scrollTo("Other Songs")
        robot.assertTextDisplayed("Other Songs")
        robot.assertTextDisplayed("Loose Thread")
    }

    @Test
    @Config(qualifiers = "w411dp-h2000dp") // tall enough that everything below the album sections is composed
    fun `grouped by album, Appears On follows the artist's albums, ahead of their other songs`() {
        val stray = createSong(id = 60, name = "Loose Thread", albumArtist = "Juniper Static", album = "Loose Tracks")
        val compilation = createAlbum(name = "Low Tide Sessions, Vol. 2", albumArtist = "Various Artists", year = 2022)
        robot.setAlbumArtist(readyAlbumArtistDetail(songs = phaseGardenSongs() + stray, albums = listOf(albumOf(phaseGardenSongs())), appearsOn = listOf(compilation)))

        robot.assertTextAbove("Phase Garden", "Appears On")
        robot.assertTextAbove("Appears On", "Other Songs")
    }

    @Test
    fun `Play plays every song and the overflow opens the artist's actions`() {
        val state = readyAlbumArtistDetail()
        robot.setAlbumArtist(state)

        robot.clickPlay()
        robot.lastPlayed shouldBe (state.songs to 0)

        robot.clickOverflow()
        robot.lastMore shouldBe state.albumArtist
    }

    @Test
    fun `Shuffle shuffles every song by the artist`() {
        robot.setAlbumArtist(readyAlbumArtistDetail())

        robot.clickShuffle()

        robot.shuffleClicked shouldBe true
    }

    @Test
    fun `without albums crediting them elsewhere there is no Appears On section`() {
        robot.setAlbumArtist(readyAlbumArtistDetail())

        robot.assertTextNotDisplayed("Appears On")
    }

    @Test
    fun `Appears On lists others' albums crediting the artist, a tap opens one and a long press opens its actions`() {
        val compilation = createAlbum(name = "Low Tide Sessions, Vol. 2", albumArtist = "Various Artists", year = 2022)
        robot.setAlbumArtist(readyAlbumArtistDetail(appearsOn = listOf(compilation)))

        robot.scrollTo("Appears On")
        robot.assertTextDisplayed("Appears On")
        robot.clickText("Low Tide Sessions, Vol. 2")
        robot.lastAppearsOnOpened shouldBe compilation

        robot.longClickText("Low Tide Sessions, Vol. 2")
        robot.lastMore shouldBe compilation
    }

    @Test
    fun `an artist only credited on others' albums counts just their songs`() {
        val feature = createAlbum(name = "Graduation", albumArtist = "Kanye West")
        robot.setAlbumArtist(readyAlbumArtistDetail(artist = createAlbumArtist(name = "Chris Martin"), albums = emptyList(), appearsOn = listOf(feature)))

        robot.assertTextDisplayed("3 songs")
        robot.assertTextNotDisplayed("Albums")
        robot.assertTextNotDisplayed("Albums & Songs")
        robot.assertTextNotDisplayed("Other Songs")
        robot.scrollTo("Appears On")
        robot.assertTextDisplayed("Appears On")
    }

    @Test
    fun `while loading there is no Play button`() {
        robot.setAlbumArtist(loadingAlbumArtistDetail)

        robot.assertTextNotDisplayed("Play")
    }
}
