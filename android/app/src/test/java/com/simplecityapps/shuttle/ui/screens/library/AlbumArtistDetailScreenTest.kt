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
    fun `grouped by album, the albums head the song list and there is no separate Albums section`() {
        robot.setAlbumArtist(readyAlbumArtistDetail())

        robot.assertTextDisplayed("Juniper Static")
        robot.assertTextDisplayed("1 album · 3 songs")
        robot.assertTextNotDisplayed("Albums")
        robot.assertTextDisplayed("Albums & Songs")
        robot.assertTextShownTimes("Phase Garden", 1)
    }

    @Test
    fun `listed flat, the Albums section comes first, then every song`() {
        robot.setAlbumArtist(readyAlbumArtistDetail(sortOrder = ArtistSongSortOrder.SongTitle))

        robot.assertTextDisplayed("Albums")
        robot.assertTextDisplayed("Phase Garden")
        robot.scrollTo("Songs")
        robot.assertTextDisplayed("Songs")
        robot.assertTextShownTimes("Soft Machines at Dawn", 1)
    }

    @Test
    fun `tapping an album's row folds it and tapping its thumbnail opens it`() {
        val state = readyAlbumArtistDetail()
        robot.setAlbumArtist(state)

        robot.clickText("Phase Garden")
        robot.lastAlbumToggled shouldBe state.albums[0]
        robot.lastAlbumClicked shouldBe null

        robot.clickOpenAlbum()
        robot.lastAlbumClicked shouldBe state.albums[0]
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
    @Config(qualifiers = "w411dp-h2000dp") // tall enough that the Songs section below the unfolded album is composed
    fun `listed flat, an unfolded album in the Albums section plays within the album`() {
        val songs = phaseGardenSongs()
        val album = albumOf(songs)
        robot.setAlbumArtist(readyAlbumArtistDetail(songs = songs, albums = listOf(album), sortOrder = ArtistSongSortOrder.SongTitle, expandedAlbums = setOfNotNull(album.groupKey)))

        robot.assertTextShownTimes("Soft Machines at Dawn", 2)
        robot.clickText("Soft Machines at Dawn")

        robot.lastPlayed shouldBe (songs to 1)
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
