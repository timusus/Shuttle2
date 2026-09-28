package com.simplecityapps.mediaprovider

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.query.SongQuery
import io.kotest.matchers.shouldBe
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class MediaImporterTest {
    private val provider = GatedProvider()
    private val preferences = GeneralPreferenceManager(InMemoryKeyValueStore())
    private val importer =
        MediaImporter(
            strings = FakeMediaImportStrings,
            songRepository = EmptySongRepository,
            playlistStore = object : ImportedPlaylistStore {
                override suspend fun storePlaylist(playlist: MediaImporter.PlaylistUpdateData) = error("ImportedPlaylistStore.storePlaylist isn't faked")
            },
            preferenceManager = preferences
        ).apply { mediaProviders += provider }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `imports started together while one is running coalesce into one follow-up pass`() = runBlocking<Unit> {
        val returned = Channel<Unit>(Channel.UNLIMITED)
        launch(Dispatchers.Default) { importer.import().also { returned.send(Unit) } }
        provider.started.receive()

        // Every import started while the first pass runs returns without waiting for it, requesting a follow-up pass
        repeat(IMPORTS) { launch(Dispatchers.Default) { importer.import().also { returned.send(Unit) } } }
        repeat(IMPORTS) { returned.receive() }
        importer.isImporting shouldBe true

        provider.gate.trySend(Unit)
        provider.started.receive()
        provider.gate.trySend(Unit)
        returned.receive()

        provider.scans.load() shouldBe 2
        importer.isImporting shouldBe false
    }

    @Test
    fun `an import after one has finished scans again`() = runBlocking<Unit> {
        provider.gate.trySend(Unit)
        provider.gate.trySend(Unit)

        importer.import()
        importer.import()

        provider.scans.load() shouldBe 2
    }

    @Test
    fun `an import requested while one is running triggers exactly one follow-up pass`() = runBlocking<Unit> {
        val returned = Channel<Unit>(Channel.UNLIMITED)
        launch(Dispatchers.Default) { importer.import().also { returned.send(Unit) } }
        provider.started.receive()

        importer.import() // requested while the first pass is running; returns immediately rather than scanning

        importer.isImporting shouldBe true
        provider.gate.trySend(Unit) // let the first pass finish
        provider.started.receive() // the follow-up pass starts
        provider.gate.trySend(Unit) // let it finish
        returned.receive()

        provider.scans.load() shouldBe 2
        importer.isImporting shouldBe false
    }

    @Test
    fun `repeated imports requested while one is running still trigger only one follow-up pass`() = runBlocking<Unit> {
        val returned = Channel<Unit>(Channel.UNLIMITED)
        launch(Dispatchers.Default) { importer.import().also { returned.send(Unit) } }
        provider.started.receive()

        repeat(3) { importer.import() } // three requests while the first pass is running

        provider.gate.trySend(Unit) // let the first pass finish
        provider.started.receive() // the single follow-up pass starts
        provider.gate.trySend(Unit) // let it finish
        returned.receive()

        provider.scans.load() shouldBe 2 // not 4 -- the three requests coalesced into one follow-up pass
        importer.isImporting shouldBe false
    }

    @Test
    fun `an import requested as the last pass finishes still runs another pass`() = runBlocking<Unit> {
        repeat(2) { provider.gate.trySend(Unit) }
        var requested = false
        // Requested after the running import has found no request pending, but before it releases the lock
        importer.beforeUnlock = {
            if (!requested) {
                requested = true
                importer.import()
            }
        }

        importer.import()

        provider.scans.load() shouldBe 2
        importer.isImporting shouldBe false
    }

    @Test
    fun `an import requested during a pass that fails still runs`() = runBlocking<Unit> {
        provider.failNext.store(true)
        val result = CompletableDeferred<Result<Unit>>()
        launch(Dispatchers.Default) { result.complete(runCatching { importer.import() }) }
        provider.started.receive()

        importer.import() // requested while the first pass is running

        provider.gate.trySend(Unit) // the first pass fails
        provider.started.receive() // the requested pass still starts
        provider.gate.trySend(Unit)

        result.await().isSuccess shouldBe true
        provider.scans.load() shouldBe 2
        importer.isImporting shouldBe false
    }

    @Test
    fun `a failing import with nothing requested throws rather than retrying`() = runBlocking<Unit> {
        provider.failNext.store(true)
        provider.gate.trySend(Unit)

        runCatching { importer.import() }.exceptionOrNull()?.message shouldBe provider.failure.message

        provider.scans.load() shouldBe 1
        importer.isImporting shouldBe false
    }

    @Test
    fun `a collector that arrives mid-import sees the import in progress and then how it ended`() = runBlocking<Unit> {
        provider.scanFailure = "Server unreachable"
        val import = launch(Dispatchers.Default) { importer.import() }
        provider.started.receive()

        importer.songImportState.value shouldBe SongImportState.ImportProgress(MediaProviderType.Shuttle, "Fetching", progress = null)

        provider.gate.trySend(Unit)
        import.join()

        importer.songImportState.value shouldBe SongImportState.ImportComplete(MediaProviderType.Shuttle, "Server unreachable")
    }

    @Test
    fun `songs imported before the current tag version are outdated until an import of every source succeeds`() = runBlocking<Unit> {
        preferences.songTagsVersion = MediaImporter.SONG_TAGS_VERSION - 1
        importer.songTagsOutdated shouldBe true

        provider.scanFailure = "Server unreachable"
        provider.gate.trySend(Unit)
        importer.import()
        importer.songTagsOutdated shouldBe true

        provider.scanFailure = null
        provider.gate.trySend(Unit)
        importer.import()
        importer.songTagsOutdated shouldBe false
        preferences.songTagsVersion shouldBe MediaImporter.SONG_TAGS_VERSION
    }

    @Test
    fun `an import that throws leaves the songs outdated`() = runBlocking<Unit> {
        provider.failNext.store(true)
        provider.gate.trySend(Unit)

        runCatching { importer.import() }

        importer.songTagsOutdated shouldBe true
    }

    /**
     * Counts its scans, signals [started] as each one begins, holds it open until a [gate] send, then throws [failure] if [failNext]
     * is set, or reports [scanFailure] if that's set.
     */
    private class GatedProvider : MediaProvider {
        override val type = MediaProviderType.Shuttle

        val scans = AtomicInt(0)
        val started = Channel<Unit>(Channel.UNLIMITED)
        val gate = Channel<Unit>(Channel.UNLIMITED)
        val failNext = AtomicBoolean(false)
        val failure = IllegalStateException("Scan failed")

        @Volatile var scanFailure: String? = null

        override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = flow {
            scans.incrementAndFetch()
            started.send(Unit)
            gate.receive()
            if (failNext.exchange(false)) throw failure
            scanFailure?.let { message -> emit(FlowEvent.Failure(message)) }
        }

        override fun findPlaylists(existingSongs: List<Song>): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>> = emptyFlow()
    }

    private companion object {
        const val IMPORTS = 8
    }

    private object FakeMediaImportStrings : MediaImportStrings {
        override fun connecting(provider: String) = "Connecting to $provider"
        override val fetching = "Fetching"
        override fun fetchingSongs(
            count: Int,
            total: Int
        ) = "Fetching $count of $total"
        override fun saving(count: Int) = "Saving $count"
        override val importError = "Import failed"
    }

    /** A repository whose queries all return an empty list; anything else fails the test. */
    private object EmptySongRepository : SongRepository {
        override fun getSongs(query: SongQuery): Flow<List<Song>?> = flowOf(emptyList())

        override val updatedSongIds: Flow<Set<Long>> = flowOf(emptySet())

        override suspend fun insert(songs: List<Song>, mediaProviderType: MediaProviderType) = notFaked()

        override suspend fun update(song: Song): Int = notFaked()

        override suspend fun update(songs: List<Song>) = notFaked()

        override suspend fun remove(song: Song) = notFaked()

        override suspend fun removeAll(mediaProviderType: MediaProviderType) = notFaked()

        override suspend fun insertUpdateAndDelete(
            inserts: List<Song>,
            updates: List<Song>,
            deletes: List<Song>,
            mediaProviderType: MediaProviderType
        ): Triple<Int, Int, Int> = notFaked()

        override suspend fun remapPaths(remaps: List<SongPathRemap>, mediaProviderType: MediaProviderType): List<SongPathRemap> = notFaked()

        override suspend fun setPlaybackPosition(song: Song, playbackPosition: Int) = notFaked()

        override suspend fun recordPlayedThrough(song: Song) = notFaked()

        override suspend fun setExcluded(songs: List<Song>, excluded: Boolean) = notFaked()

        override suspend fun clearExcludeList() = notFaked()

        override suspend fun setFavourite(songs: List<Song>, favourite: Boolean) = notFaked()

        private fun notFaked(): Nothing = error("SongRepository call isn't faked")
    }
}
