package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createGenre
import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.persistence.LibraryTab
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.actions.MediaActionType
import com.simplecityapps.shuttle.ui.screens.library.albumartists.readyAlbumArtistList
import com.simplecityapps.shuttle.ui.screens.library.albums.readyAlbumList
import com.simplecityapps.shuttle.ui.screens.library.folders.createFolder
import com.simplecityapps.shuttle.ui.screens.library.folders.emptyFolderList
import com.simplecityapps.shuttle.ui.screens.library.folders.readyFolderList
import com.simplecityapps.shuttle.ui.screens.library.genres.readyGenreList
import com.simplecityapps.shuttle.ui.screens.library.playlists.readyPlaylistList
import com.simplecityapps.shuttle.ui.screens.library.songs.emptySongList
import com.simplecityapps.shuttle.ui.screens.library.songs.loadingSongList
import com.simplecityapps.shuttle.ui.screens.library.songs.readySongList
import com.simplecityapps.shuttle.ui.screens.sources.MusicAccess
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
    fun `shows the title, the enabled sections in order and the current tab's count`() {
        robot.setContent(libraryState(), libraryControls(count = "12 songs"))

        robot.assertTextDisplayed("Library")
        robot.assertTextDisplayed("12 songs")
        robot.tabLabels() shouldBe listOf("Songs", "Albums", "Artists", "Playlists", "Genres")
    }

    @Test
    fun `the large top bar collapses as the page scrolls`() {
        val songs = (1..60L).map { createSong(id = it, name = "Track $it") }
        robot.setContent(
            libraryState(currentTab = LibraryTab.Songs),
            libraryControls(count = "60 songs"),
            LibraryPageStates(songs = readySongList(songs, sortOrder = SongSortOrder.SongName)),
        )
        val titleTop = robot.titleTop()

        robot.dragPageUp()

        (robot.titleTop() < titleTop) shouldBe true
    }

    @Test
    fun `the section row leaves the first chip fully visible and scrolls the last one into view`() {
        robot.setContent(libraryState(currentTab = LibraryTab.Songs), libraryControls(count = "1 song"))
        robot.sectionChipIsFullyVisible("Songs") shouldBe true

        repeat(4) { robot.swipeToNextPage() }

        robot.assertTabSelected("Genres")
        robot.sectionChipIsFullyVisible("Genres") shouldBe true
    }

    @Test
    fun `right to left, selecting the last section scrolls its chip fully into view`() {
        robot.setContent(libraryState(currentTab = LibraryTab.Songs), layoutDirection = LayoutDirection.Rtl)
        robot.sectionChipIsFullyVisible("Songs") shouldBe true

        repeat(4) { robot.swipeToNextPage() }

        robot.assertTabSelected("Genres")
        robot.sectionChipIsFullyVisible("Genres") shouldBe true
    }

    @Test
    fun `right to left, opening on the last section shows its chip fully`() {
        robot.setContent(libraryState(currentTab = LibraryTab.Genres), layoutDirection = LayoutDirection.Rtl)

        robot.sectionChipIsFullyVisible("Genres") shouldBe true
    }

    @Test
    fun `hidden tabs stay out of the section chips and the order follows the saved order`() {
        robot.setContent(
            libraryState(
                allTabs = listOf(LibraryTab.Songs, LibraryTab.Albums, LibraryTab.Genres, LibraryTab.Playlists, LibraryTab.Artists, LibraryTab.Folders),
                enabledTabs = setOf(LibraryTab.Songs, LibraryTab.Albums, LibraryTab.Folders),
            ),
        )

        robot.tabLabels() shouldBe listOf("Songs", "Albums", "Folders")
    }

    @Test
    fun `opens on the saved tab, and a chip tap moves the pager to its page`() {
        robot.setContent(libraryState(currentTab = LibraryTab.Albums))

        robot.assertTabSelected("Albums")
        robot.assertTextDisplayed("page:Albums")
        robot.clickTab("Songs")

        robot.assertTabSelected("Songs")
        robot.assertTextDisplayed("page:Songs")
        robot.lastTabSelected shouldBe LibraryTab.Songs
    }

    @Test
    fun `swiping the pager selects the next section's chip`() {
        robot.setContent(libraryState(currentTab = LibraryTab.Artists))

        robot.swipeToNextPage()

        robot.assertTabSelected("Playlists")
        robot.assertTextDisplayed("page:Playlists")
        robot.lastTabSelected shouldBe LibraryTab.Playlists
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
    fun `the settings action opens settings`() {
        robot.setContent(libraryState())

        robot.openSettings()

        robot.settingsOpened shouldBe true
    }

    @Test
    fun `the last days of the trial show a chip that opens the Pro screen`() {
        robot.setContent(libraryState(), trialDaysLeft = 2)

        robot.assertTextDisplayed("2 days left")
        robot.openTrial()

        robot.trialOpened shouldBe true
    }

    @Test
    fun `no chip shows outside the trial's last days`() {
        robot.setContent(libraryState())

        robot.trialChipShown() shouldBe false
    }

    @Test
    fun `the Edit chip ends the section row and opens the tab editor`() {
        robot.setContent(libraryState(), libraryControls(sortOptions = sorts("Song Name", "Year")))

        robot.openTabEditor()

        robot.assertTextDisplayed("Edit tabs")
        robot.countOfText("Year") shouldBe 0
    }

    @Test
    fun `the sort button names the current sort and opens the tab's sorts`() {
        var sortedBy: String? = null
        val options = listOf("Song Name", "Year").map { label -> S2Action(label, { sortedBy = label }, selected = label == "Song Name") }
        robot.setContent(libraryState(currentTab = LibraryTab.Songs), libraryControls(count = "2 songs", sortOptions = options))

        robot.assertTextDisplayed("Song Name")
        robot.openSort()
        robot.clickText("Year")

        sortedBy shouldBe "Year"
    }

    @Test
    fun `the sort button announces itself as the sort, with a full-size touch target`() {
        robot.setContent(libraryState(currentTab = LibraryTab.Albums), libraryControls(count = "2 albums", sortOptions = sorts("Album name", "Year")))

        robot.assertSortAnnounced("Sort: Album name")
        (robot.sortTouchTarget().height >= 48.dp) shouldBe true
    }

    @Test
    fun `the controls row scrolls away with the page and leaves the screen`() {
        val songs = (1..60L).map { createSong(id = it, name = "Track $it") }
        robot.setContent(
            libraryState(currentTab = LibraryTab.Songs),
            libraryControls(count = "60 songs", sortOptions = sorts("Song Name"), onPlay = {}, onShuffle = {}),
            LibraryPageStates(songs = readySongList(songs, sortOrder = SongSortOrder.SongName)),
        )
        robot.controlsShown() shouldBe true

        robot.scrollPageTo("library-songs", 40)

        robot.controlsShown() shouldBe false
        robot.assertContentDescriptionNotDisplayed("Play")
        robot.assertTabSelected("Songs")
    }

    @Test
    fun `an empty page still shows its controls above the empty state`() {
        robot.setContent(
            libraryState(currentTab = LibraryTab.Songs),
            libraryControls(count = "0 songs", sortOptions = sorts("Song Name")),
            LibraryPageStates(songs = emptySongList()),
        )

        robot.assertTextDisplayed("0 songs")
        robot.assertTextDisplayed("Song Name")
        robot.assertTextDisplayed("No songs")
    }

    @Test
    fun `a loading page still shows its controls`() {
        robot.setContent(libraryState(currentTab = LibraryTab.Songs), libraryControls(count = "0 songs"), LibraryPageStates(songs = loadingSongList))

        robot.assertTextDisplayed("0 songs")
    }

    @Test
    fun `a tab without sorts, views or play has no controls`() {
        robot.setContent(libraryState(currentTab = LibraryTab.Albums), libraryControls(count = "1 album"))

        robot.assertTextDisplayed("1 album")
        robot.assertContentDescriptionNotDisplayed("Layout: List")
        robot.assertContentDescriptionNotDisplayed("Play")
        robot.assertContentDescriptionNotDisplayed("Shuffle")
    }

    @Test
    fun `the artists tab has the songs tab's controls, count and sort and shuffle and play`() {
        var sortedBy: String? = null
        val options = listOf("Artist Name", "Album Count").map { label -> S2Action(label, { sortedBy = label }, selected = label == "Artist Name") }
        robot.setContent(
            libraryState(currentTab = LibraryTab.Artists),
            libraryControls(count = "2 artists", sortOptions = options, viewMode = ViewMode.List, onPlay = {}, onShuffle = {}),
            LibraryPageStates(artists = readyAlbumArtistList(listOf(createAlbumArtist(name = "Harbour Owl"), createAlbumArtist(name = "Juniper Static")))),
        )

        robot.assertTextDisplayed("2 artists")
        robot.assertTextDisplayed("Artist Name")
        robot.assertContentDescriptionDisplayed("Shuffle")
        robot.assertContentDescriptionDisplayed("Play")
        robot.openSort()
        robot.clickText("Album Count")
        sortedBy shouldBe "Album Count"
        robot.clickContentDescription("Play")
        robot.playClicked shouldBe true
    }

    @Test
    fun `at double text size every control stays on screen and the row grows past its minimum height`() {
        robot.setContent(
            libraryState(currentTab = LibraryTab.Albums),
            libraryControls(count = "1,284 albums", sortOptions = sorts("Album Name", "Artist Name"), viewMode = ViewMode.Grid, onPlay = {}, onShuffle = {}),
            fontScale = 2f,
        )

        robot.controlsRowFitsScreen() shouldBe true
        robot.assertSortAnnounced("Sort: Album Name")
        (robot.controlsRowSize().height >= 48.dp) shouldBe true
    }

    @Test
    fun `the layout menu switches the albums page between grid, list and compact grid`() {
        val albums = listOf(
            createAlbum(name = "Phase Garden", albumArtist = "Juniper Static"),
            createAlbum(name = "Salt Lines", albumArtist = "Harbour Owl"),
            createAlbum(name = "Tin Orchard", albumArtist = "Juniper Static"),
        )
        robot.setAlbumsWithViewToggle(readyAlbumList(albums, viewMode = ViewMode.Grid))
        robot.sideBySide("Phase Garden", "Salt Lines") shouldBe true
        robot.sideBySide("Phase Garden", "Tin Orchard") shouldBe false

        robot.clickContentDescription("Layout: Grid")
        robot.clickText("List")

        robot.lastViewModeChange shouldBe ViewMode.List
        robot.sideBySide("Phase Garden", "Salt Lines") shouldBe false

        // A phone fits three compact tiles a row, where the grid fits two.
        robot.clickContentDescription("Layout: List")
        robot.clickText("Compact grid")

        robot.lastViewModeChange shouldBe ViewMode.CompactGrid
        robot.sideBySide("Phase Garden", "Tin Orchard") shouldBe true
    }

    @Test
    fun `the tab editor hides a tab and moves one up`() {
        robot.setContent(libraryState())

        robot.openTabEditor()
        robot.toggleTabSwitch(LibraryTab.Genres)
        robot.lastTabsChanged shouldBe (LibraryTab.entries to LibraryTab.defaultEnabled.toSet() - LibraryTab.Genres)

        robot.moveTabUp(1)
        robot.lastTabsChanged!!.first shouldBe listOf(LibraryTab.Albums, LibraryTab.Songs) + LibraryTab.entries.drop(2)
    }

    // -- Selection --

    @Test
    fun `a selection swaps the top bar for the selection toolbar`() {
        robot.setContent(libraryState(), chrome = selectingChrome(selectedCount = 3))

        robot.assertTextDisplayed("3 selected")
        robot.assertTextNotDisplayed("Library")
    }

    @Test
    fun `selection toolbar actions report their type`() {
        robot.setContent(libraryState(), chrome = selectingChrome())

        robot.clickSelectionAction("Add to queue")

        robot.lastSelectionAction shouldBe MediaActionType.AddToQueue
    }

    @Test
    fun `clearing the selection reports it`() {
        robot.setContent(libraryState(), chrome = selectingChrome())

        robot.clearSelection()

        robot.selectionCleared shouldBe true
    }

    @Test
    fun `back with a selection clears it rather than leaving the Library`() {
        robot.setContent(libraryState(), chrome = selectingChrome(selectedCount = 1))

        robot.pressBack()

        robot.selectionCleared shouldBe true
    }

    @Test
    fun `without a selection back is left to the back stack`() {
        robot.setContent(libraryState())

        robot.backIsHandled() shouldBe false
    }

    // -- Pages --

    @Test
    fun `songs page plays a tapped song, and the controls play everything and shuffle`() {
        val song = createSong(id = 1, name = "Chlorophyll Loop")
        robot.setContent(
            libraryState(currentTab = LibraryTab.Songs),
            libraryControls(onPlay = {}, onShuffle = {}),
            LibraryPageStates(songs = readySongList(listOf(song, createSong(id = 2, name = "Lucky")))),
        )

        robot.clickText("Chlorophyll Loop")
        robot.lastSongClicked shouldBe song

        robot.clickContentDescription("Play")
        robot.playClicked shouldBe true

        robot.clickContentDescription("Shuffle")
        robot.shuffleClicked shouldBe true
    }

    @Test
    fun `the song count shows once, in the controls row (#491)`() {
        robot.setContent(
            libraryState(currentTab = LibraryTab.Songs),
            libraryControls(count = "2 songs"),
            LibraryPageStates(songs = readySongList(listOf(createSong(id = 1, name = "Chlorophyll Loop"), createSong(id = 2, name = "Lucky")))),
        )

        robot.countOfText("2 songs") shouldBe 1
    }

    @Test
    fun `albums controls play and shuffle every album, with the count once`() {
        val albums = listOf(createAlbum(name = "Phase Garden", albumArtist = "Juniper Static"))
        robot.setContent(
            libraryState(currentTab = LibraryTab.Albums),
            libraryControls(count = "1 album", viewMode = ViewMode.Grid, onPlay = {}, onShuffle = {}),
            LibraryPageStates(albums = readyAlbumList(albums)),
        )

        robot.countOfText("1 album") shouldBe 1
        robot.assertContentDescriptionDisplayed("Layout: Grid")

        robot.clickContentDescription("Play")
        robot.playClicked shouldBe true

        robot.clickContentDescription("Shuffle")
        robot.shuffleClicked shouldBe true
    }

    @Test
    fun `dragging the songs page's letter scroller to the bottom jumps to the last letter (#491)`() {
        val songs = ('A'..'Z').mapIndexed { i, letter -> createSong(id = i.toLong(), name = "$letter song") }
        robot.setContent(libraryState(currentTab = LibraryTab.Songs), pages = LibraryPageStates(songs = readySongList(songs, sortOrder = SongSortOrder.SongName)))

        robot.dragFastScrollerToBottom()

        robot.assertTextNotDisplayed("A song")
        robot.assertTextDisplayed("Z song")
    }

    @Test
    fun `songs sorted by album sit under one header per album, with track numbers instead of covers (#491)`() {
        val songs = listOf(
            createSong(id = 1, name = "Opening", album = "Phase Garden", albumArtist = "Juniper Static", track = 1),
            createSong(id = 2, name = "Closing", album = "Phase Garden", albumArtist = "Juniper Static", track = 2),
            createSong(id = 3, name = "Lone", album = "Salt Lines", albumArtist = "Harbour Owl", track = 1),
        )
        robot.setContent(libraryState(currentTab = LibraryTab.Songs), pages = LibraryPageStates(songs = readySongList(songs, sortOrder = SongSortOrder.AlbumGroupKey)))

        robot.countOfText("Phase Garden") shouldBe 1
        robot.countOfText("Salt Lines") shouldBe 1
        robot.assertTextDisplayed("Closing")
    }

    @Test
    fun `songs sorted by name keep the album in each row`() {
        val songs = listOf(createSong(id = 1, name = "Opening", album = "Phase Garden", albumArtist = "Juniper Static"))
        robot.setContent(libraryState(currentTab = LibraryTab.Songs), pages = LibraryPageStates(songs = readySongList(songs, sortOrder = SongSortOrder.SongName)))

        robot.countOfText("Phase Garden") shouldBe 1
    }

    @Test
    fun `songs sorted by duration keep a plain thumb that still reaches the end`() {
        val songs = (1..48).map { createSong(id = it.toLong(), name = "Track $it", duration = it) }
        robot.setContent(libraryState(currentTab = LibraryTab.Songs), pages = LibraryPageStates(songs = readySongList(songs, sortOrder = SongSortOrder.Duration)))

        robot.dragFastScrollerToBottom()

        robot.assertTextNotDisplayed("Track 1")
        robot.assertTextDisplayed("Track 48")
    }

    @Test
    fun `an empty songs page says so`() {
        robot.setContent(libraryState(currentTab = LibraryTab.Songs), pages = LibraryPageStates(songs = emptySongList()))

        robot.assertTextDisplayed("No songs")
    }

    @Test
    fun `albums page opens a tapped album`() {
        val album = createAlbum(name = "Phase Garden", albumArtist = "Juniper Static")
        robot.setContent(libraryState(currentTab = LibraryTab.Albums), pages = LibraryPageStates(albums = readyAlbumList(listOf(album))))

        robot.clickText("Phase Garden")

        robot.lastAlbumClicked shouldBe album
    }

    @Test
    fun `the fast scroller sits at the page's end edge`() {
        val albums = listOf(createAlbum(name = "Phase Garden", albumArtist = "Juniper Static"))
        robot.setContent(libraryState(currentTab = LibraryTab.Albums), pages = LibraryPageStates(albums = readyAlbumList(albums, viewMode = ViewMode.List)))
        robot.assertFastScrollerAtEndEdge()
    }

    @Test
    fun `the fast scroller sits at the end edge of a grid page too`() {
        val albums = listOf(createAlbum(name = "Phase Garden", albumArtist = "Juniper Static"))
        robot.setContent(libraryState(currentTab = LibraryTab.Albums), pages = LibraryPageStates(albums = readyAlbumList(albums, viewMode = ViewMode.Grid)))
        robot.assertFastScrollerAtEndEdge()
    }

    @Test
    fun `the playlists page has a fast scroller at its end edge too`() {
        robot.setContent(libraryState(currentTab = LibraryTab.Playlists), pages = LibraryPageStates(playlists = readyPlaylistList(listOf(createPlaylist(name = "Road trip")))))
        robot.assertFastScrollerAtEndEdge()
    }

    @Test
    fun `artists page opens a tapped artist`() {
        val artist = createAlbumArtist(name = "Juniper Static")
        robot.setContent(libraryState(currentTab = LibraryTab.Artists), pages = LibraryPageStates(artists = readyAlbumArtistList(listOf(artist))))

        robot.clickText("Juniper Static")

        robot.lastArtistClicked shouldBe artist
    }

    @Test
    fun `genres page groups thousands in song counts`() {
        robot.setContent(libraryState(currentTab = LibraryTab.Genres), pages = LibraryPageStates(genres = readyGenreList(listOf(createGenre(name = "Dance", songCount = 1727)))))

        robot.assertTextDisplayed("1,727 songs")
    }

    @Test
    fun `genres page opens a tapped genre`() {
        val genre = createGenre(name = "Shoegaze")
        robot.setContent(libraryState(currentTab = LibraryTab.Genres), pages = LibraryPageStates(genres = readyGenreList(listOf(genre))))

        robot.clickText("Shoegaze")

        robot.lastGenreClicked shouldBe genre
    }

    @Test
    fun `genres page still lists and opens a genre that has a cover patchwork (#135)`() {
        val genre = createGenre(name = "Shoegaze")
        val covers = (1..4L).map { createSong(id = it, name = "Track $it", album = "Album $it") }
        robot.setContent(
            libraryState(currentTab = LibraryTab.Genres),
            pages = LibraryPageStates(genres = readyGenreList(listOf(genre)), genreCovers = mapOf("Shoegaze" to covers)),
        )

        robot.clickText("Shoegaze")

        robot.lastGenreClicked shouldBe genre
    }

    @Test
    fun `playlists page lists smart playlists, opens a playlist and starts a new one`() {
        val playlist = createPlaylist(name = "Road trip")
        val smartPlaylists = SmartPlaylistId.entries.map { it.smartPlaylist }
        robot.setContent(
            libraryState(currentTab = LibraryTab.Playlists),
            pages = LibraryPageStates(playlists = readyPlaylistList(listOf(playlist), smartPlaylists = smartPlaylists)),
        )

        robot.assertTextDisplayed("Recently Added")
        robot.assertTextDisplayed("Most Played")
        robot.clickText("History")
        robot.lastSmartPlaylistClicked shouldBe SmartPlaylistId.History.smartPlaylist

        robot.clickText("Road trip")
        robot.lastPlaylistClicked shouldBe playlist

        robot.clickText("New playlist")
        robot.newPlaylistClicked shouldBe true
    }

    @Test
    fun `playlists page lists Favorites first among the smart playlists`() {
        val smartPlaylists = SmartPlaylistId.entries.map { it.smartPlaylist }
        robot.setContent(
            libraryState(currentTab = LibraryTab.Playlists),
            pages = LibraryPageStates(playlists = readyPlaylistList(smartPlaylists = smartPlaylists)),
        )

        robot.assertTextDisplayed("Favorites")

        robot.clickText("Favorites")
        robot.lastSmartPlaylistClicked shouldBe SmartPlaylistId.Favourites.smartPlaylist
    }

    @Test
    fun `empty folders page explains itself and offers to add a folder`() {
        robot.setContent(
            libraryState(enabledTabs = setOf(LibraryTab.Folders), currentTab = LibraryTab.Folders),
            pages = LibraryPageStates(folders = emptyFolderList()),
        )

        robot.assertTextDisplayed("Folders show music stored on this device. Server music isn't listed here.")
        robot.clickText("Add folder")

        robot.addFolderClicked shouldBe true
    }

    @Test
    fun `playlists page with no playlists says how to make one`() {
        robot.setContent(
            libraryState(enabledTabs = setOf(LibraryTab.Playlists), currentTab = LibraryTab.Playlists),
            pages = LibraryPageStates(playlists = readyPlaylistList(playlists = emptyList())),
        )

        robot.assertTextDisplayed("No playlists yet. Tap New playlist to create one.")
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

    @Test
    fun `folders page shows friendly names for storage volumes`() {
        robot.setContent(
            libraryState(enabledTabs = setOf(LibraryTab.Folders), currentTab = LibraryTab.Folders),
            pages = LibraryPageStates(
                folders = readyFolderList(
                    folders = listOf(
                        createFolder("primary"),
                        createFolder("1234-ABCD"),
                        createFolder("<other>"),
                    ),
                ),
            ),
        )

        robot.assertTextDisplayed("Internal storage")
        robot.assertTextDisplayed("1234-ABCD")
        robot.assertTextDisplayed("Other locations")
    }

    @Test
    fun `folders page breadcrumb leaves out the volume and jumps to a tapped ancestor`() {
        robot.setContent(
            libraryState(enabledTabs = setOf(LibraryTab.Folders), currentTab = LibraryTab.Folders),
            pages = LibraryPageStates(
                folders = readyFolderList(
                    currentFolder = createFolder("primary", "Music", "Juniper Static"),
                    folders = emptyList(),
                ),
            ),
        )

        robot.assertTextDisplayed("Juniper Static")
        robot.clickBreadcrumb("Music")

        robot.lastFolderClicked shouldBe createFolder("primary", "Music")
    }

    @Test
    fun `folders page breadcrumb root jumps to the top level`() {
        robot.setContent(
            libraryState(enabledTabs = setOf(LibraryTab.Folders), currentTab = LibraryTab.Folders),
            pages = LibraryPageStates(
                folders = readyFolderList(
                    currentFolder = createFolder("primary", "Music", "Juniper Static"),
                    folders = emptyList(),
                ),
            ),
        )

        robot.clickBreadcrumb("Folders")

        robot.rootClicked shouldBe true
    }

    @Test
    fun `an empty library offers to add a folder and has no offline toggle`() {
        robot.setEmptyLibrary(LibraryAvailability.Empty(MusicAccess.Granted))

        robot.assertTextDisplayed("Add folder")
        robot.assertOfflineToggleNotDisplayed()
        robot.clickText("Add folder")

        robot.addFolderClicked shouldBe true
    }

    @Test
    fun `a library syncing from a server shows the sync, not the permission prompt`() {
        robot.setEmptyLibrary(LibraryAvailability.Empty(MusicAccess.NotRequested, ScanProgress("Syncing Emby", 0.5f), hasServer = true))

        robot.assertTextDisplayed("Syncing your music")
        robot.assertTextDisplayed("Syncing Emby")
        robot.assertTextNotDisplayed("Allow access to music")
    }

    @Test
    fun `a library with a server waiting on its first import shows the sync`() {
        robot.setEmptyLibrary(LibraryAvailability.Empty(MusicAccess.NotRequested, ScanProgress(), hasServer = true))

        robot.assertTextDisplayed("Syncing your music")
        robot.assertTextNotDisplayed("Allow access to music")
    }

    @Test
    fun `a library with only this device and no permission asks for access`() {
        robot.setEmptyLibrary(LibraryAvailability.Empty(MusicAccess.NotRequested))

        robot.assertTextDisplayed("Allow access to music")
    }
}
