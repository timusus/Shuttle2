package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createGenre
import com.simplecityapps.createPlaylist
import com.simplecityapps.createSmartPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.persistence.LibraryTab
import com.simplecityapps.shuttle.ui.actions.MediaActionType
import com.simplecityapps.shuttle.ui.screens.library.albumartists.readyAlbumArtistList
import com.simplecityapps.shuttle.ui.screens.library.albums.readyAlbumList
import com.simplecityapps.shuttle.ui.screens.library.folders.createFolder
import com.simplecityapps.shuttle.ui.screens.library.folders.readyFolderList
import com.simplecityapps.shuttle.ui.screens.library.genres.readyGenreList
import com.simplecityapps.shuttle.ui.screens.library.playlists.readyPlaylistList
import com.simplecityapps.shuttle.ui.screens.library.songs.emptySongList
import com.simplecityapps.shuttle.ui.screens.library.songs.readySongList
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = LibraryScreenRobot(composeTestRule)

    // -- Container chrome --

    @Test
    fun `shows the title, the current tab's count and the enabled tabs in order`() {
        robot.setContent(libraryState(), chromeWithMenu(subtitle = "12 songs"))

        robot.assertTextDisplayed("Library")
        robot.assertTextDisplayed("12 songs")
        robot.tabLabels() shouldBe listOf("Genres", "Playlists", "Artists", "Albums", "Songs")
    }

    @Test
    fun `hidden tabs stay out of the tab row and the order follows the saved order`() {
        robot.setContent(
            libraryState(
                allTabs = listOf(LibraryTab.Songs, LibraryTab.Albums, LibraryTab.Genres, LibraryTab.Playlists, LibraryTab.Artists, LibraryTab.Folders),
                enabledTabs = setOf(LibraryTab.Songs, LibraryTab.Albums, LibraryTab.Folders),
            ),
        )

        robot.tabLabels() shouldBe listOf("Songs", "Albums", "Folders")
    }

    @Test
    fun `opens on the saved tab and reports a tab tap`() {
        robot.setContent(libraryState(currentTab = LibraryTab.Albums))

        robot.assertTabSelected("Albums")
        robot.clickTab("Songs")

        robot.assertTabSelected("Songs")
        robot.lastTabSelected shouldBe LibraryTab.Songs
    }

    @Test
    fun `every tab hidden shows an empty state that opens the tab editor`() {
        robot.setContent(hiddenTabsLibrary())

        robot.assertTextDisplayed("Every tab is hidden")
        robot.clickText("Edit tabs")

        robot.toggleTabSwitch(LibraryTab.Songs)
        robot.lastTabsChanged shouldBe (LibraryTab.entries to setOf(LibraryTab.Songs))
    }

    @Test
    fun `the overflow lists the tab's options above Edit tabs`() {
        var sorted = false
        robot.setContent(libraryState(), chromeWithMenu(menu = listOf(listOf(S2Action("Song Name", { sorted = true }, selected = true)))))

        robot.openOverflow()
        robot.assertTextDisplayed("Edit tabs")
        robot.clickText("Song Name")

        sorted shouldBe true
    }

    @Test
    fun `the tab editor hides a tab and moves one up`() {
        robot.setContent(libraryState())

        robot.openOverflow()
        robot.clickText("Edit tabs")
        robot.toggleTabSwitch(LibraryTab.Genres)
        robot.lastTabsChanged shouldBe (LibraryTab.entries to LibraryTab.defaultEnabled.toSet() - LibraryTab.Genres)

        robot.moveTabUp(1)
        robot.lastTabsChanged!!.first shouldBe listOf(LibraryTab.Playlists, LibraryTab.Genres) + LibraryTab.entries.drop(2)
    }

    // -- Selection --

    @Test
    fun `a selection swaps the top bar for the selection toolbar`() {
        robot.setContent(libraryState(), selectingChrome(selectedCount = 3))

        robot.assertTextDisplayed("3 selected")
        robot.assertTextNotDisplayed("Library")
    }

    @Test
    fun `selection toolbar actions report their type`() {
        robot.setContent(libraryState(), selectingChrome())

        robot.clickSelectionAction("Add to Queue")

        robot.lastSelectionAction shouldBe MediaActionType.AddToQueue
    }

    @Test
    fun `clearing the selection reports it`() {
        robot.setContent(libraryState(), selectingChrome())

        robot.clearSelection()

        robot.selectionCleared shouldBe true
    }

    // -- Pages --

    @Test
    fun `songs page plays a tapped song, selects on long press and shuffles`() {
        val song = createSong(id = 1, name = "Airbag")
        robot.setContent(libraryState(currentTab = LibraryTab.Songs), pages = LibraryPageStates(songs = readySongList(listOf(song, createSong(id = 2, name = "Lucky")))))

        robot.clickText("Airbag")
        robot.lastSongClicked shouldBe song

        robot.clickText("Shuffle")
        robot.shuffleClicked shouldBe true
    }

    @Test
    fun `an empty songs page says so`() {
        robot.setContent(libraryState(currentTab = LibraryTab.Songs), pages = LibraryPageStates(songs = emptySongList()))

        robot.assertTextDisplayed("No songs")
    }

    @Test
    fun `albums page opens a tapped album`() {
        val album = createAlbum(name = "OK Computer", albumArtist = "Radiohead")
        robot.setContent(libraryState(currentTab = LibraryTab.Albums), pages = LibraryPageStates(albums = readyAlbumList(listOf(album))))

        robot.clickText("OK Computer")

        robot.lastAlbumClicked shouldBe album
    }

    @Test
    fun `artists page opens a tapped artist`() {
        val artist = createAlbumArtist(name = "Radiohead")
        robot.setContent(libraryState(currentTab = LibraryTab.Artists), pages = LibraryPageStates(artists = readyAlbumArtistList(listOf(artist))))

        robot.clickText("Radiohead")

        robot.lastArtistClicked shouldBe artist
    }

    @Test
    fun `genres page opens a tapped genre`() {
        val genre = createGenre(name = "Shoegaze")
        robot.setContent(libraryState(currentTab = LibraryTab.Genres), pages = LibraryPageStates(genres = readyGenreList(listOf(genre))))

        robot.clickText("Shoegaze")

        robot.lastGenreClicked shouldBe genre
    }

    @Test
    fun `playlists page lists smart playlists, opens a playlist and starts a new one`() {
        val playlist = createPlaylist(name = "Road trip")
        val smart = createSmartPlaylist()
        robot.setContent(
            libraryState(currentTab = LibraryTab.Playlists),
            pages = LibraryPageStates(playlists = readyPlaylistList(listOf(playlist), smartPlaylists = listOf(smart))),
        )

        robot.clickText("Recently Added")
        robot.lastSmartPlaylistClicked shouldBe smart

        robot.clickText("Road trip")
        robot.lastPlaylistClicked shouldBe playlist

        robot.clickText("New Playlist")
        robot.newPlaylistClicked shouldBe true
    }

    @Test
    fun `folders page opens a tapped folder`() {
        val folder = createFolder("primary", "Music")
        robot.setContent(
            libraryState(enabledTabs = setOf(LibraryTab.Folders), currentTab = LibraryTab.Folders),
            pages = LibraryPageStates(folders = readyFolderList(folders = listOf(folder))),
        )

        robot.clickText("Music")

        robot.lastFolderClicked shouldBe folder
    }
}
