package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumArtistRepository
import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ObserveSetting
import com.simplecityapps.shuttle.settings.SaveSetting
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.sorting.ArtistSongSortOrder
import com.simplecityapps.shuttle.ui.actions.ObserveCurrentSong
import com.simplecityapps.shuttle.ui.actions.ShuffleAlbums
import com.simplecityapps.shuttle.ui.screens.library.SortPreferenceManager
import com.simplecityapps.shuttle.ui.theme.ArtworkSeed
import com.simplecityapps.shuttle.ui.theme.ArtworkSeedSource
import com.simplecityapps.shuttle.ui.theme.ObserveArtworkSeed
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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

    private val seededAlbums = mutableListOf<String?>()
    private val seedSource = ArtworkSeedSource { song ->
        seededAlbums += song.album
        ArtworkSeed.Available(RED)
    }
    private val settingsStore = SettingsStore(InMemoryKeyValueStore())
    private val sortStore = InMemoryKeyValueStore()
    private val sortPreferences = SortPreferenceManager(sortStore)

    private val fakeAlbumArtistRepository = FakeAlbumArtistRepository()
    private val fakeAlbumRepository = FakeAlbumRepository()
    private val fakeSongRepository = FakeSongRepository()
    private val fakePlaylistRepository = FakePlaylistRepository()
    private val fakeQueueOperations = FakeQueueOperations()
    private val shuffleQueueOperations = FakeQueueOperations()
    private val shufflePlaybackOperations = FakePlaybackOperations()

    private val testArtist = createAlbumArtist(name = "The Tin Orchards", albumCount = 2, songCount = 2)

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

        viewModel.onAlbumClick(albumA)
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

        viewModel.onAlbumClick(cassette)
        viewModel.onAlbumClick(change)
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

        viewModel.onAlbumClick(cassette)
        advanceUntilIdle()
        fakeAlbumRepository.setAlbums(listOf(change))
        advanceUntilIdle()
        viewModel.onAlbumClick(change)
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
    fun `the newest album's artwork tints the screen`() = runTest {
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

        viewModel.uiState.value.seed shouldBe ArtworkSeed.Available(RED)
        seededAlbums shouldBe listOf("Loose Change")
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
    fun `top songs are those played at least twice - most played first`() = runTest {
        val viewModel = loadedViewModel()

        viewModel.uiState.value.topSongs.map { it.name } shouldBe listOf("Ember", "Rewind Button", "Pocket Lint")
    }

    @Test
    fun `top songs stop at ten`() = runTest {
        val songs = (1..12).map { song(it.toLong(), "Song $it", "Cassette Summer", track = it, playCount = it + 1) }
        val viewModel = loadedViewModel(albums = listOf(cassetteSummer), songs = songs)

        viewModel.uiState.value.topSongs.map { it.playCount } shouldBe (13 downTo 4).toList()
    }

    @Test
    fun `no song played twice leaves top songs empty`() = runTest {
        val viewModel = loadedViewModel(songs = discography.map { it.copy(playCount = 1) })

        viewModel.uiState.value.topSongs shouldBe emptyList()
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
            observeAlbumArtists = testMediaActions.observeAlbumArtists,
            observeAlbums = testMediaActions.observeAlbums,
            observeSongs = testMediaActions.observeSongs,
            observeCurrentSong = ObserveCurrentSong(fakeQueueOperations),
            observeArtworkSeed = ObserveArtworkSeed(seedSource, ObserveSetting(settingsStore)),
            shuffleAlbums = ShuffleAlbums(shuffleQueueOperations, shufflePlaybackOperations),
            sortPreferences = sortPreferences,
        )
    }
}
