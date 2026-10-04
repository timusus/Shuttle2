package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumArtistRepository
import com.simplecityapps.fakes.FakeAlbumListPreferences
import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.FakeArtistListPreferences
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumIdentityRule
import com.simplecityapps.shuttle.model.ArtistHeroArtwork
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.withAlbumIdentities
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ObserveSetting
import com.simplecityapps.shuttle.settings.SaveSetting
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.sorting.ArtistSongSortOrder
import com.simplecityapps.shuttle.ui.actions.ObserveCurrentSong
import com.simplecityapps.shuttle.ui.actions.ShuffleAlbums
import com.simplecityapps.shuttle.ui.screens.library.LibraryViewPreferences
import com.simplecityapps.shuttle.ui.screens.library.ReadLibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.SaveLibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.SortPreferenceManager
import com.simplecityapps.shuttle.ui.theme.ArtworkSeed
import com.simplecityapps.shuttle.ui.theme.ArtworkSeedSource
import com.simplecityapps.shuttle.ui.theme.ObserveArtistArtworkSeed
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** The seed the fake artwork source extracts, as sRGB ARGB. */
private const val RED = 0xFFFF0000.toInt()

private const val ARTIST = "The Tin Orchards"

/** Focused ViewModel unit tests for behaviour that can't be observed through the UI. */
@ExperimentalCoroutinesApi
class AlbumArtistDetailViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** How long the fake artwork source takes to extract a seed, in virtual time. */
    private var seedDelayMs = 0L
    private val seededHeroes = mutableListOf<ArtistHeroArtwork>()
    private val seedSource = object : ArtworkSeedSource {
        override suspend fun seedFor(song: Song): ArtworkSeed = error("An artist page seeds from the image its hero shows")

        override suspend fun seedFor(hero: ArtistHeroArtwork): ArtworkSeed {
            delay(seedDelayMs)
            seededHeroes += hero
            return ArtworkSeed.Available(RED)
        }
    }
    private val settingsStore = SettingsStore(InMemoryKeyValueStore())
    private val sortStore = InMemoryKeyValueStore()
    private val libraryViewPreferences = LibraryViewPreferences(SortPreferenceManager(sortStore), FakeAlbumListPreferences(), FakeArtistListPreferences())

    private val fakeAlbumArtistRepository = FakeAlbumArtistRepository()
    private val fakeAlbumRepository = FakeAlbumRepository()
    private val fakeSongRepository = FakeSongRepository()
    private val fakePlaylistRepository = FakePlaylistRepository()
    private val fakeQueueOperations = FakeQueueOperations()
    private val shuffleQueueOperations = FakeQueueOperations()
    private val shufflePlaybackOperations = FakePlaybackOperations()

    // Keyed as the identity rule keys the songs' album artist, so their albums are the artist's own, not Appears On
    private val testArtist = createAlbumArtist(name = "The Tin Orchards", albumCount = 2, songCount = 2, groupKey = AlbumArtistGroupKey(AlbumIdentityRule.artistKey("The Tin Orchards")))

    @Test
    fun `shows loading when repository has not emitted`() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.loadingState shouldBe AlbumArtistDetailUiState.LoadingState.Loading
    }

    @Test
    fun `shows empty when no albums and no songs`() = runTest {
        fakeAlbumArtistRepository.setAlbumArtists(listOf(testArtist))
        fakeSongRepository.setSongs(emptyList())
        fakeAlbumRepository.setAlbums(emptyList())
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.loadingState shouldBe AlbumArtistDetailUiState.LoadingState.Empty
    }

    @Test
    fun `albums they only appear on follow in Appears On and their songs there are in the song list`() = runTest {
        fakeAlbumArtistRepository.setAlbumArtists(listOf(testArtist))
        fakeSongRepository.applyQueryPredicates = true
        fakeAlbumRepository.applyQueryPredicates = true
        val songs = listOf(
            createSong(id = 1, album = "Lantern Hours", albumArtist = "The Tin Orchards", artists = listOf("The Tin Orchards"), path = "/m/1.mp3"),
            createSong(id = 2, album = "Harbour Lights", albumArtist = "Maya Reyes", artists = listOf("Maya Reyes feat. The Tin Orchards"), path = "/m/2.mp3"),
            createSong(id = 3, album = "Summer Hits", albumArtist = "Various Artists", artists = listOf("The Tin Orchards"), path = "/m/3.mp3"),
            createSong(id = 4, album = "Night Drive", albumArtist = "Maya Reyes", artists = listOf("Maya Reyes"), path = "/m/4.mp3"),
        ).withAlbumIdentities()
        fakeSongRepository.setSongs(songs)
        fakeAlbumRepository.setAlbums(songs.map { song -> createAlbum(name = song.album!!, albumArtist = song.albumArtist, groupKey = song.albumGroupKey) })
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        state.albums.map { it.name } shouldBe listOf("Lantern Hours")
        state.appearsOn.map { it.name } shouldBe listOf("Harbour Lights", "Summer Hits")
        state.songs.map { it.id }.sorted() shouldBe listOf(1L, 2L, 3L)
    }

    @Test
    fun `no Appears On for an artist credited only on their own albums`() = runTest {
        fakeAlbumArtistRepository.setAlbumArtists(listOf(testArtist))
        fakeSongRepository.applyQueryPredicates = true
        fakeAlbumRepository.applyQueryPredicates = true
        val songs = listOf(createSong(id = 1, album = "Lantern Hours", albumArtist = "The Tin Orchards", artists = listOf("The Tin Orchards"))).withAlbumIdentities()
        fakeSongRepository.setSongs(songs)
        fakeAlbumRepository.setAlbums(listOf(createAlbum(name = "Lantern Hours", albumArtist = "The Tin Orchards", groupKey = songs.single().albumGroupKey)))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.appearsOn shouldBe emptyList()
    }

    @Test
    fun `albums sorted by year descending`() = runTest {
        fakeAlbumArtistRepository.setAlbumArtists(listOf(testArtist))
        val lanternHours = createAlbum(name = "Lantern Hours", albumArtist = "The Tin Orchards", year = 1963)
        val looseChange = createAlbum(name = "Loose Change", albumArtist = "The Tin Orchards", year = 1970)
        val cassetteSummer = createAlbum(name = "Cassette Summer", albumArtist = "The Tin Orchards", year = 1969)
        fakeAlbumRepository.setAlbums(listOf(lanternHours, looseChange, cassetteSummer))
        fakeSongRepository.setSongs(listOf(createSong(albumArtist = "The Tin Orchards")))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.albums shouldBe listOf(looseChange, cassetteSummer, lanternHours)
    }

    @Test
    fun `expanded album survives a re-emission of new instances with the same groupKey`() = runTest {
        val albumA = createAlbum(name = "Cassette Summer", albumArtist = "The Tin Orchards", year = 1969)
        val albumB = createAlbum(name = "Loose Change", albumArtist = "The Tin Orchards", year = 1970)
        fakeAlbumArtistRepository.setAlbumArtists(listOf(testArtist))
        val albumC = createAlbum(name = "Lantern Hours", albumArtist = "The Tin Orchards", year = 1963)
        fakeAlbumRepository.setAlbums(listOf(albumA, albumB, albumC))
        fakeSongRepository.setSongs(
            listOf(
                createSong(id = 1, name = "Rewind Button", album = "Cassette Summer"),
                createSong(id = 2, name = "Loose Change", album = "Loose Change"),
            )
        )

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onToggleAlbum(albumA)
        advanceUntilIdle()
        viewModel.uiState.value.expandedAlbums shouldBe setOf(albumA.groupKey)

        // Repository re-emits new instances with matching name/artist (same groupKey), e.g. after a rescan.
        val albumARescanned = createAlbum(name = "Cassette Summer", albumArtist = "The Tin Orchards", year = 1969)
        val albumBRescanned = createAlbum(name = "Loose Change", albumArtist = "The Tin Orchards", year = 1970)
        fakeAlbumRepository.setAlbums(listOf(albumARescanned, albumBRescanned, albumC))
        fakeSongRepository.setSongs(
            listOf(
                createSong(id = 1, name = "Rewind Button", album = "Cassette Summer"),
                createSong(id = 2, name = "Loose Change", album = "Loose Change"),
            )
        )
        advanceUntilIdle()

        viewModel.uiState.value.expandedAlbums shouldBe setOf(albumARescanned.groupKey)
    }

    @Test
    fun `an expanded album that a rescan removes drops out of the expanded albums`() = runTest {
        val cassette = createAlbum(name = "Cassette Summer", albumArtist = "The Tin Orchards", year = 1969)
        val change = createAlbum(name = "Loose Change", albumArtist = "The Tin Orchards", year = 1970)
        val lantern = createAlbum(name = "Lantern Hours", albumArtist = "The Tin Orchards", year = 1963)
        fakeAlbumArtistRepository.setAlbumArtists(listOf(testArtist))
        fakeAlbumRepository.setAlbums(listOf(cassette, change, lantern))
        fakeSongRepository.setSongs(listOf(createSong(albumArtist = "The Tin Orchards")))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onToggleAlbum(cassette)
        viewModel.onToggleAlbum(change)
        advanceUntilIdle()

        fakeAlbumRepository.setAlbums(listOf(change))
        advanceUntilIdle()

        viewModel.uiState.value.expandedAlbums shouldBe setOf(change.groupKey)
    }

    @Test
    fun `a removed album comes back collapsed once another album has been toggled`() = runTest {
        val cassette = createAlbum(name = "Cassette Summer", albumArtist = "The Tin Orchards", year = 1969)
        val change = createAlbum(name = "Loose Change", albumArtist = "The Tin Orchards", year = 1970)
        val lantern = createAlbum(name = "Lantern Hours", albumArtist = "The Tin Orchards", year = 1963)
        fakeAlbumArtistRepository.setAlbumArtists(listOf(testArtist))
        fakeAlbumRepository.setAlbums(listOf(cassette, change, lantern))
        fakeSongRepository.setSongs(listOf(createSong(albumArtist = "The Tin Orchards")))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onToggleAlbum(cassette)
        advanceUntilIdle()
        fakeAlbumRepository.setAlbums(listOf(change))
        advanceUntilIdle()
        viewModel.onToggleAlbum(change)
        advanceUntilIdle()

        fakeAlbumRepository.setAlbums(listOf(cassette, change))
        advanceUntilIdle()

        viewModel.uiState.value.expandedAlbums shouldBe setOf(change.groupKey)
    }

    @Test
    fun `shuffle albums plays every album in turn - each in track order`() = runTest {
        val cassette = listOf(1, 2, 3).map { createSong(id = it.toLong(), name = "Cassette $it", albumArtist = "The Tin Orchards", album = "Cassette Summer", track = it) }
        val change = listOf(1, 2).map { createSong(id = 10L + it, name = "Change $it", albumArtist = "The Tin Orchards", album = "Loose Change", track = it) }
        fakeAlbumArtistRepository.setAlbumArtists(listOf(testArtist))
        fakeAlbumRepository.setAlbums(
            listOf(
                createAlbum(name = "Cassette Summer", albumArtist = "The Tin Orchards", year = 1969),
                createAlbum(name = "Loose Change", albumArtist = "The Tin Orchards", year = 1970),
            )
        )
        fakeSongRepository.setSongs(cassette + change)
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onShuffleAlbums()
        advanceUntilIdle()

        val queue = shuffleQueueOperations.lastSetQueue.orEmpty()
        queue.chunkedByAlbum() shouldBeIn listOf(listOf(cassette, change), listOf(change, cassette))
        shufflePlaybackOperations.calls shouldBe listOf("play()")
    }

    @Test
    fun `a failed shuffle albums holds a message until the screen consumes it`() = runTest {
        fakeAlbumArtistRepository.setAlbumArtists(listOf(testArtist))
        fakeSongRepository.setSongs(listOf(createSong(albumArtist = "The Tin Orchards")))
        shufflePlaybackOperations.loadResult = Result.failure(IllegalStateException("File not found"))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onShuffleAlbums()
        advanceUntilIdle()

        val event = viewModel.uiState.value.events.single()
        event.value shouldBe AlbumArtistDetailEvent.ShuffleAlbumsFailed("File not found")

        viewModel.onEventHandled(event.id)
        advanceUntilIdle()

        viewModel.uiState.value.events shouldBe emptyList()
    }

    private fun List<Song>.chunkedByAlbum(): List<List<Song>> = fold(mutableListOf<MutableList<Song>>()) { runs, song ->
        if (runs.lastOrNull()?.last()?.album == song.album) runs.last() += song else runs += mutableListOf(song)
        runs
    }

    @Test
    fun `the hero's image tints the screen - the artist's own else their top album's cover`() = runTest {
        val lanternHours = createSong(id = 1, album = "Lantern Hours", albumArtist = "The Tin Orchards")
        val looseChange = createSong(id = 2, album = "Loose Change", albumArtist = "The Tin Orchards")
        fakeAlbumArtistRepository.setAlbumArtists(listOf(testArtist))
        fakeAlbumRepository.setAlbums(
            listOf(
                createAlbum(name = "Lantern Hours", albumArtist = "The Tin Orchards", year = 1963, groupKey = lanternHours.albumGroupKey),
                createAlbum(name = "Loose Change", albumArtist = "The Tin Orchards", year = 1970, groupKey = looseChange.albumGroupKey),
            ),
        )
        fakeSongRepository.setSongs(listOf(lanternHours, looseChange))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val hero = viewModel.uiState.value.hero
        hero?.artist?.name shouldBe "The Tin Orchards"
        // Untagged with a MusicBrainz id, so the online lookup isn't trusted; nothing played, so the newest album
        hero?.onlineLookup shouldBe false
        hero?.fallbackAlbum?.name shouldBe "Loose Change"
        viewModel.uiState.value.seed shouldBe ArtworkSeed.Available(RED)
        seededHeroes shouldBe listOf(hero)
    }

    @Test
    fun `a slow artwork seed doesn't hold up the content - the tint follows once it's extracted`() = runTest {
        seedDelayMs = 5_000
        fakeAlbumArtistRepository.setAlbumArtists(listOf(testArtist))
        fakeAlbumRepository.setAlbums(listOf(looseChange))
        fakeSongRepository.setSongs(listOf(song(1, "Arcade", "Loose Change")))
        val viewModel = createViewModel()
        var firstContentAt: Long? = null
        backgroundScope.launch {
            viewModel.uiState.collect { state ->
                if (firstContentAt == null && state.loadingState == AlbumArtistDetailUiState.LoadingState.Ready) firstContentAt = currentTime
            }
        }
        advanceTimeBy(1)

        firstContentAt shouldBe 0L
        viewModel.uiState.value.songs.map { it.name } shouldBe listOf("Arcade")
        viewModel.uiState.value.seed shouldBe ArtworkSeed.Loading

        advanceUntilIdle()
        viewModel.uiState.value.seed shouldBe ArtworkSeed.Available(RED)
    }

    @Test
    fun `turning Colour from artwork off drops the tint`() = runTest {
        fakeAlbumArtistRepository.setAlbumArtists(listOf(testArtist))
        fakeAlbumRepository.setAlbums(listOf(createAlbum(name = "Loose Change", albumArtist = "The Tin Orchards", year = 1970)))
        fakeSongRepository.setSongs(listOf(createSong(id = 2, album = "Loose Change", albumArtist = "The Tin Orchards")))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        SaveSetting(settingsStore)(AppearanceSettings.ColourFromArtwork, false)
        advanceUntilIdle()

        viewModel.uiState.value.seed shouldBe ArtworkSeed.None
    }

    private fun song(id: Long, name: String, album: String, track: Int = 1, disc: Int = 1, playCount: Int = 0) = createSong(id = id, name = name, albumArtist = ARTIST, album = album, track = track, disc = disc, playCount = playCount)

    /** An album keyed the way its songs group, so the sections can match them. */
    private fun album(name: String, year: Int?) = createAlbum(name = name, albumArtist = ARTIST, year = year, groupKey = createSong(albumArtist = ARTIST, album = name).albumGroupKey)

    private val lanternHours = album("Lantern Hours", 1963)
    private val cassetteSummer = album("Cassette Summer", 1969)
    private val looseChange = album("Loose Change", 1970)
    private val discography = listOf(
        song(1, "Wick", "Lantern Hours", track = 2, playCount = 1),
        song(2, "Ember", "Lantern Hours", track = 1, playCount = 5),
        song(3, "Rewind Button", "Cassette Summer", playCount = 3),
        song(4, "Small Coins", "Loose Change", track = 2, disc = 1),
        song(5, "Pocket Lint", "Loose Change", track = 1, disc = 2, playCount = 2),
        song(6, "Arcade", "Loose Change", track = 1, disc = 1),
    )

    private fun TestScope.loadedViewModel(
        albums: List<Album> = listOf(lanternHours, cassetteSummer, looseChange),
        songs: List<Song> = discography,
    ): AlbumArtistDetailViewModel {
        fakeAlbumArtistRepository.setAlbumArtists(listOf(testArtist))
        fakeAlbumRepository.setAlbums(albums)
        fakeSongRepository.setSongs(songs)
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    private fun AlbumArtistDetailViewModel.sectionNames() = uiState.value.sections.map { section -> section.album?.name to section.songs.map { it.name } }

    @Test
    fun `album newest sections each album newest first with its tracks in disc and track order`() = runTest {
        val viewModel = loadedViewModel()

        viewModel.uiState.value.sortOrder shouldBe ArtistSongSortOrder.AlbumNewest
        viewModel.sectionNames() shouldBe listOf(
            "Loose Change" to listOf("Arcade", "Small Coins", "Pocket Lint"),
            "Cassette Summer" to listOf("Rewind Button"),
            "Lantern Hours" to listOf("Ember", "Wick"),
        )
    }

    @Test
    fun `album oldest and album title reorder the sections but not the carousel`() = runTest {
        val viewModel = loadedViewModel()

        viewModel.onSortOrderSelected(ArtistSongSortOrder.AlbumOldest)
        advanceUntilIdle()
        viewModel.uiState.value.sections.map { it.album?.name } shouldBe listOf("Lantern Hours", "Cassette Summer", "Loose Change")

        viewModel.onSortOrderSelected(ArtistSongSortOrder.AlbumTitle)
        advanceUntilIdle()
        viewModel.uiState.value.sections.map { it.album?.name } shouldBe listOf("Cassette Summer", "Lantern Hours", "Loose Change")
        viewModel.uiState.value.albums shouldBe listOf(looseChange, cassetteSummer, lanternHours)
    }

    @Test
    fun `song title and most played list every song in one flat section`() = runTest {
        val viewModel = loadedViewModel()

        viewModel.onSortOrderSelected(ArtistSongSortOrder.SongTitle)
        advanceUntilIdle()
        viewModel.sectionNames() shouldBe listOf(null to listOf("Arcade", "Ember", "Pocket Lint", "Rewind Button", "Small Coins", "Wick"))

        viewModel.onSortOrderSelected(ArtistSongSortOrder.MostPlayed)
        advanceUntilIdle()
        viewModel.sectionNames() shouldBe listOf(null to listOf("Ember", "Rewind Button", "Pocket Lint", "Wick", "Arcade", "Small Coins"))
    }

    @Test
    fun `songs without one of the artist's albums trail the album sections as other songs`() = runTest {
        val viewModel = loadedViewModel(
            albums = listOf(cassetteSummer),
            songs = listOf(song(1, "Stray B", "Loose Tracks"), song(2, "Rewind Button", "Cassette Summer"), song(3, "Stray A", "Loose Tracks")),
        )

        viewModel.sectionNames() shouldBe listOf(
            "Cassette Summer" to listOf("Rewind Button"),
            null to listOf("Stray A", "Stray B"),
        )
    }

    @Test
    fun `play order follows the visible sort across every section - collapsed ones included`() = runTest {
        val viewModel = loadedViewModel()
        viewModel.uiState.value.expandedAlbums shouldBe emptySet()

        viewModel.uiState.value.songs.map { it.name } shouldBe listOf("Arcade", "Small Coins", "Pocket Lint", "Rewind Button", "Ember", "Wick")

        viewModel.onSortOrderSelected(ArtistSongSortOrder.AlbumOldest)
        advanceUntilIdle()
        viewModel.uiState.value.songs.map { it.name } shouldBe listOf("Ember", "Wick", "Rewind Button", "Arcade", "Small Coins", "Pocket Lint")
    }

    @Test
    fun `the chosen sort is saved and the next artist opens with it`() = runTest {
        val viewModel = loadedViewModel()

        viewModel.onSortOrderSelected(ArtistSongSortOrder.MostPlayed)
        advanceUntilIdle()

        sortStore.values["sort_order_artist_detail"] shouldBe "MostPlayed"
        loadedViewModel().uiState.value.sortOrder shouldBe ArtistSongSortOrder.MostPlayed
    }

    @Test
    fun `two albums start expanded`() = runTest {
        val viewModel = loadedViewModel(albums = listOf(cassetteSummer, looseChange))

        viewModel.uiState.value.expandedAlbums shouldBe setOf(cassetteSummer.groupKey, looseChange.groupKey)
    }

    @Test
    fun `three albums start collapsed`() = runTest {
        val viewModel = loadedViewModel()

        viewModel.uiState.value.expandedAlbums shouldBe emptySet()
    }

    @Test
    fun `a rescan doesn't reapply the default expansion`() = runTest {
        val viewModel = loadedViewModel(albums = listOf(cassetteSummer, looseChange))

        fakeAlbumRepository.setAlbums(listOf(lanternHours, cassetteSummer, looseChange))
        advanceUntilIdle()
        viewModel.uiState.value.expandedAlbums shouldBe setOf(cassetteSummer.groupKey, looseChange.groupKey)

        viewModel.onCollapseAll()
        fakeAlbumRepository.setAlbums(listOf(cassetteSummer))
        advanceUntilIdle()
        viewModel.uiState.value.expandedAlbums shouldBe emptySet()
    }

    @Test
    fun `expand all and collapse all unfold and fold every album`() = runTest {
        val viewModel = loadedViewModel()

        viewModel.onExpandAll()
        advanceUntilIdle()
        viewModel.uiState.value.expandedAlbums shouldBe setOf(lanternHours.groupKey, cassetteSummer.groupKey, looseChange.groupKey)

        viewModel.onCollapseAll()
        advanceUntilIdle()
        viewModel.uiState.value.expandedAlbums shouldBe emptySet()
    }

    @Test
    fun `the albums shelf hides while songs group by album - their headers are the albums`() = runTest {
        val viewModel = loadedViewModel()

        ArtistSongSortOrder.entries.forEach { order ->
            viewModel.onSortOrderSelected(order)
            advanceUntilIdle()
            viewModel.uiState.value.showAlbumsShelf shouldBe !order.groupsByAlbum
        }
    }

    @Test
    fun `the albums shelf shows under an album order with no album sections`() = runTest {
        // Songs crediting the artist on none of their own albums trail as other songs, so nothing else lists the albums
        val viewModel = loadedViewModel(albums = listOf(cassetteSummer), songs = listOf(song(1, "Stray", "Loose Tracks")))

        viewModel.uiState.value.sortOrder.groupsByAlbum shouldBe true
        viewModel.uiState.value.showAlbumsShelf shouldBe true
    }

    @Test
    fun `no albums no shelf`() = runTest {
        val viewModel = loadedViewModel(albums = emptyList(), songs = listOf(song(1, "Stray", "Loose Tracks")))

        viewModel.onSortOrderSelected(ArtistSongSortOrder.SongTitle)
        advanceUntilIdle()
        viewModel.uiState.value.showAlbumsShelf shouldBe false
    }

    private fun createViewModel(): AlbumArtistDetailViewModel {
        val testMediaActions = TestMediaActions(
            fakeSongRepository,
            FakeGenreRepository(),
            fakePlaylistRepository,
            FakeQueueOperations(),
            playbackOperations = FakePlaybackOperations(),
            albumRepository = fakeAlbumRepository,
            albumArtistRepository = fakeAlbumArtistRepository,
        )
        return AlbumArtistDetailViewModel(
            groupKey = testArtist.groupKey,
            observeArtists = testMediaActions.observeArtists,
            observeArtistAlbums = testMediaActions.observeArtistAlbums,
            observeSongs = testMediaActions.observeSongs,
            observeCurrentSong = ObserveCurrentSong(fakeQueueOperations),
            observeArtistArtworkSeed = ObserveArtistArtworkSeed(seedSource, ObserveSetting(settingsStore)),
            shuffleAlbums = ShuffleAlbums(shuffleQueueOperations, shufflePlaybackOperations),
            readSetting = ReadLibraryViewSetting(libraryViewPreferences),
            saveSetting = SaveLibraryViewSetting(libraryViewPreferences),
        )
    }
}
