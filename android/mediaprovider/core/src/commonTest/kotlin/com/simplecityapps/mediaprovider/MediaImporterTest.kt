package com.simplecityapps.mediaprovider

import com.simplecityapps.mediaprovider.MediaImporter.Companion.songTagsOutdated
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.query.SongQuery
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield

@OptIn(ExperimentalCoroutinesApi::class)
class MediaImporterTest {
    private val provider = GatedProvider()
    private val preferences = GeneralPreferenceManager(InMemoryKeyValueStore())
    private val clock = FakeClock(Instant.parse("2026-10-04T09:00:00Z"))
    private val songRepository = FakeSongRepository()
    private val server = ServerProvider()
    private val importer =
        MediaImporter(
            strings = FakeMediaImportStrings,
            songRepository = songRepository,
            playlistStore = object : ImportedPlaylistStore {
                override suspend fun storePlaylist(playlist: MediaImporter.PlaylistUpdateData) = error("ImportedPlaylistStore.storePlaylist isn't faked")
            },
            preferenceManager = preferences,
            afterImport = {},
            clock = clock
        ).apply { mediaProviders += provider }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // The library has been imported under this build, so a sync has nothing waiting on the first import
        preferences.lastMediaImportDate = clock.time
        preferences.songTagsRescanVersion = MediaImporter.SONG_TAGS_VERSION
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
    fun `each provider keeps how its own import ended whichever reported last`() = runBlocking<Unit> {
        val server = GatedProvider(MediaProviderType.Jellyfin).apply { scanFailure = "Server unreachable" }
        importer.mediaProviders += server
        val import = launch(Dispatchers.Default) { importer.import() }
        provider.started.receive()
        server.started.receive()

        server.gate.trySend(Unit)
        while (importer.providerImportStates.value[MediaProviderType.Jellyfin] !is SongImportState.ImportComplete) yield()

        importer.providerImportStates.value[MediaProviderType.Shuttle] shouldBe SongImportState.ImportProgress(MediaProviderType.Shuttle, "Fetching", progress = null)

        provider.gate.trySend(Unit)
        import.join()

        importer.providerImportStates.value shouldBe mapOf(
            MediaProviderType.Shuttle to SongImportState.ImportComplete(MediaProviderType.Shuttle, error = null),
            MediaProviderType.Jellyfin to SongImportState.ImportComplete(MediaProviderType.Jellyfin, "Server unreachable"),
        )
    }

    @Test
    fun `a source that fails stays outdated while the others are marked current and the launch re-import runs once`() = runBlocking<Unit> {
        val server = GatedProvider(MediaProviderType.Jellyfin).apply { scanFailure = "Server unreachable" }
        importer.mediaProviders += server
        preferences.songTagsRescanVersion = 0
        importer.songTagsOutdated shouldBe true

        provider.gate.trySend(Unit)
        server.gate.trySend(Unit)
        importer.import()

        preferences.songTagsOutdated(MediaProviderType.Shuttle) shouldBe false
        preferences.songTagsOutdated(MediaProviderType.Jellyfin) shouldBe true
        // The failing server catches up on its own next import, rather than every source importing again at each launch
        importer.songTagsOutdated shouldBe false

        provider.gate.trySend(Unit)
        server.scanFailure = null
        server.gate.trySend(Unit)
        importer.import()

        preferences.songTagsOutdated(MediaProviderType.Jellyfin) shouldBe false
    }

    @Test
    fun `an import that throws leaves the source outdated`() = runBlocking<Unit> {
        provider.failNext.store(true)
        provider.gate.trySend(Unit)

        runCatching { importer.import() }

        preferences.songTagsOutdated(MediaProviderType.Shuttle) shouldBe true
    }

    @Test
    fun `a provider hears its songs are stored only when the import succeeds`() = runBlocking<Unit> {
        provider.scanFailure = "Unreadable"
        provider.gate.trySend(Unit)
        importer.import()
        provider.stored.load() shouldBe 0

        provider.scanFailure = null
        provider.gate.trySend(Unit)
        importer.import()
        provider.stored.load() shouldBe 1
    }

    @Test
    fun `a full import notes when each source's sync started - once it succeeds`() = runBlocking<Unit> {
        provider.scanFailure = "Unreachable"
        provider.gate.trySend(Unit)
        importer.import()
        preferences.lastSyncStart(provider.type.name) shouldBe null

        provider.scanFailure = null
        provider.gate.trySend(Unit)
        importer.import()
        preferences.lastSyncStart(provider.type.name) shouldBe clock.time
        preferences.lastFullSyncStart(provider.type.name) shouldBe clock.time
    }

    @Test
    fun `a sync on return to the app asks a server only for what changed since its last sync, and leaves this device alone`() = runBlocking<Unit> {
        importer.mediaProviders += server
        songRepository.stored = listOf(song())
        preferences.setSongTagsVersion(server.type.name, MediaImporter.SONG_TAGS_VERSION)
        preferences.setLastSyncStart(server.type.name, clock.time - 1.hours)
        preferences.setLastFullSyncStart(server.type.name, clock.time - 1.days)

        importer.sync(SyncTrigger.Foreground)

        server.requests shouldBe listOf(clock.time - 1.hours - SyncPolicy.OVERLAP)
        provider.scans.load() shouldBe 0
        preferences.lastSyncStart(server.type.name) shouldBe clock.time
        preferences.lastFullSyncStart(server.type.name) shouldBe clock.time - 1.days
        // Nothing changed, so the playlists are as they were
        server.playlistRequests shouldBe 0
    }

    @Test
    fun `an import that finds the stored songs unchanged writes no rows`() = runBlocking<Unit> {
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        songRepository.stored = listOf(song(), song().copy(id = 2, path = "jellyfin://item/2"))
        server.found = songRepository.stored.map { it.copy(id = 0) }

        importer.import()

        songRepository.writes shouldBe listOf(0)
    }

    @Test
    fun `a sync shows no progress, which would replace the library with the scanning state, only how it ended`() = runBlocking<Unit> {
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        val states = mutableListOf<SongImportState>()
        val collecting = launch(Dispatchers.Unconfined) { importer.songImportState.toList(states) }

        importer.sync(SyncTrigger.Foreground)
        collecting.cancel()

        // The server had nothing new, so there's nothing to say
        states shouldBe listOf(SongImportState.Idle)
    }

    @Test
    fun `a sync that stored a change says so, so what shows the library reloads`() = runBlocking<Unit> {
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        server.found = listOf(song())
        val states = mutableListOf<SongImportState>()
        val collecting = launch(Dispatchers.Unconfined) { importer.songImportState.toList(states) }

        importer.sync(SyncTrigger.Foreground)
        collecting.cancel()

        states shouldBe listOf(SongImportState.Idle, SongImportState.ImportComplete(server.type, error = null))
    }

    @Test
    fun `a server that can't be reached on return to the app keeps its status, but the daily sync reports it`() = runBlocking<Unit> {
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        server.failure = "Unreachable"

        importer.sync(SyncTrigger.Foreground)
        importer.providerImportStates.value[server.type] shouldBe null

        importer.sync(SyncTrigger.Periodic)
        importer.providerImportStates.value[server.type] shouldBe SongImportState.ImportComplete(server.type, "Unreachable")
    }

    @Test
    fun `a quiet sync that stores nothing still clears an earlier failure`() = runBlocking<Unit> {
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        songRepository.stored = listOf(song())
        preferences.setLastSyncStart(server.type.name, clock.time - 1.hours)
        preferences.setLastFullSyncStart(server.type.name, clock.time - 1.days)
        server.failure = "Unreachable"
        importer.sync(SyncTrigger.Periodic)
        importer.providerImportStates.value[server.type] shouldBe SongImportState.ImportComplete(server.type, "Unreachable")

        server.failure = null
        server.found = emptyList()
        importer.sync(SyncTrigger.Foreground)

        importer.providerImportStates.value[server.type] shouldBe SongImportState.ImportComplete(server.type, error = null)
    }

    @Test
    fun `the daily sync fetches the playlists of a server synced in the last few minutes`() = runBlocking<Unit> {
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        songRepository.stored = listOf(song())
        preferences.setLastSyncStart(server.type.name, clock.time - 5.minutes)
        preferences.setLastFullSyncStart(server.type.name, clock.time - 1.days)

        importer.sync(SyncTrigger.Periodic)

        server.playlistRequests shouldBe 1
    }

    @Test
    fun `a sync waits for the first import, and for the one a build with new tags is due`() = runBlocking<Unit> {
        importer.mediaProviders -= provider
        importer.mediaProviders += server

        preferences.lastMediaImportDate = null
        importer.sync(SyncTrigger.Periodic)
        preferences.lastMediaImportDate = clock.time
        preferences.songTagsRescanVersion = MediaImporter.SONG_TAGS_VERSION - 1
        importer.sync(SyncTrigger.Periodic)

        server.requests shouldBe emptyList()
    }

    @Test
    fun `a server synced in the last few minutes is left alone`() = runBlocking<Unit> {
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        preferences.setLastSyncStart(server.type.name, clock.time - 5.minutes)

        importer.sync(SyncTrigger.Foreground)

        server.requests shouldBe emptyList()
    }

    @Test
    fun `a full listing that came up short stores what it found but deletes nothing`() = runBlocking<Unit> {
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        val stored = song()
        val added = song().copy(id = 2, path = "/added")
        songRepository.stored = listOf(stored)
        server.found = listOf(added)
        server.listingComplete = false

        importer.sync(SyncTrigger.Periodic)

        songRepository.changes shouldBe Triple(1, 0, 0)
    }

    @Test
    fun `a complete full listing deletes the songs it no longer holds`() = runBlocking<Unit> {
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        songRepository.stored = listOf(song())
        server.found = listOf(song().copy(id = 2, path = "/added"))

        importer.sync(SyncTrigger.Periodic)

        songRepository.changes shouldBe Triple(1, 0, 1)
    }

    @Test
    fun `a server with nothing stored is synced in full, whenever it last synced`() = runBlocking<Unit> {
        importer.mediaProviders += server
        preferences.setLastSyncStart(server.type.name, clock.time - 1.hours)
        preferences.setLastFullSyncStart(server.type.name, clock.time - 1.days)

        importer.sync(SyncTrigger.Foreground)

        server.requests shouldBe listOf(null)
        preferences.lastFullSyncStart(server.type.name) shouldBe clock.time
    }

    @Test
    fun `the daily sync reads this device in full and fetches every source's playlists`() = runBlocking<Unit> {
        importer.mediaProviders += server
        songRepository.stored = listOf(song())
        preferences.setSongTagsVersion(server.type.name, MediaImporter.SONG_TAGS_VERSION)
        preferences.setLastSyncStart(server.type.name, clock.time - 1.days)
        preferences.setLastFullSyncStart(server.type.name, clock.time - 1.days)
        provider.gate.trySend(Unit)

        importer.sync(SyncTrigger.Periodic)

        provider.scans.load() shouldBe 1
        server.requests shouldBe listOf(clock.time - 1.days - SyncPolicy.OVERLAP)
        server.playlistRequests shouldBe 1
    }

    @Test
    fun `a sync asked for while an import runs returns at once`() = runBlocking<Unit> {
        val running = launch(Dispatchers.Default) { importer.import() }
        provider.started.receive()

        // A scan of its own would wait on the gate
        importer.sync(SyncTrigger.Periodic)

        provider.gate.trySend(Unit)
        running.join()
        provider.scans.load() shouldBe 1
    }

    @Test
    fun `a full import that would remove most of a source's songs keeps them until the next one finds them gone too`() = runBlocking<Unit> {
        val songs = (1L..30L).map { id -> song(id = id, path = "jellyfin://item/$id") }
        songRepository.stored = songs
        server.found = songs.take(5)

        serverImporter().import()
        songRepository.deleted[server.type].orEmpty().shouldBeEmpty()

        // The next import runs in a new process, as the periodic one usually does: only the preferences carry over
        serverImporter().import()
        songRepository.deleted[server.type].orEmpty().map { song -> song.id } shouldBe (6L..30L).toList()
    }

    @Test
    fun `an import after the user changed folders removes this device's songs at once, but keeps those under an unreadable root and a server's`() = runBlocking<Unit> {
        val device = ServerProvider(MediaProviderType.Shuttle)
        val deviceSongs = (1L..30L).map { id -> song(id = id, path = "/storage/emulated/0/Music/$id.mp3") }
        val cardSongs = (31L..32L).map { id -> song(id = id, path = "/storage/CARD/Music/$id.mp3") }
        songRepository.stored = deviceSongs + cardSongs
        device.found = deviceSongs.take(5)
        device.unreadableRoots = setOf("/storage/CARD/")
        server.found = emptyList()
        val importer = serverImporter().apply { mediaProviders += device }

        importer.import(foldersChanged = true)

        songRepository.deleted[device.type].orEmpty().map { song -> song.id } shouldBe (6L..30L).toList()
        songRepository.deleted[server.type].orEmpty().shouldBeEmpty()
    }

    @Test
    fun `a full sync that holds back a mass removal makes the next sync full, to apply it`() = runBlocking<Unit> {
        val songs = (1L..30L).map { id -> song(id = id, path = "jellyfin://item/$id") }
        songRepository.stored = songs
        server.found = songs.take(5)
        preferences.setSongTagsVersion(server.type.name, MediaImporter.SONG_TAGS_VERSION)
        preferences.setLastSyncStart(server.type.name, clock.time - 8.days)
        preferences.setLastFullSyncStart(server.type.name, clock.time - 8.days)
        val importer = serverImporter()

        importer.sync(SyncTrigger.Periodic)
        songRepository.deleted[server.type].orEmpty().shouldBeEmpty()
        preferences.lastSyncStart(server.type.name) shouldBe clock.time
        preferences.lastFullSyncStart(server.type.name) shouldBe null

        clock.time += 1.hours
        importer.sync(SyncTrigger.Foreground)
        server.requests shouldBe listOf(null, null)
        songRepository.deleted[server.type].orEmpty().map { song -> song.id } shouldBe (6L..30L).toList()
        preferences.lastFullSyncStart(server.type.name) shouldBe clock.time
    }

    @Test
    fun `a full import keeps the songs under a root the source couldn't read`() = runBlocking<Unit> {
        songRepository.stored = listOf(song(id = 1, path = "jellyfin://library/a/1"), song(id = 2, path = "jellyfin://library/b/2"), song(id = 3))
        server.found = listOf(song(id = 3))
        server.unreadableRoots = setOf("jellyfin://library/a/")

        serverImporter().import()

        songRepository.deleted[server.type].orEmpty().map { song -> song.id } shouldBe listOf(2L)
    }

    /** An importer of [server] alone. */
    private fun serverImporter() = MediaImporter(
        strings = FakeMediaImportStrings,
        songRepository = songRepository,
        playlistStore = object : ImportedPlaylistStore {
            override suspend fun storePlaylist(playlist: MediaImporter.PlaylistUpdateData) = error("ImportedPlaylistStore.storePlaylist isn't faked")
        },
        preferenceManager = preferences,
        afterImport = {},
        clock = clock
    ).apply { mediaProviders += server }

    /** A server that records the time each song request asked from (null for every song) and finds no songs. */
    private class ServerProvider(
        override val type: MediaProviderType = MediaProviderType.Jellyfin
    ) : IncrementalMediaProvider {

        val requests = mutableListOf<Instant?>()
        var playlistRequests = 0

        /** What it finds, or the failure it reports instead. */
        var found: List<Song> = emptyList()
        var failure: String? = null
        var listingComplete = true

        override fun lastListingComplete() = listingComplete

        override var unreadableRoots: Set<String> = emptySet()

        override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = songs(since = null)

        override fun findSongsChangedSince(
            existingSongs: List<Song>,
            since: Instant
        ): Flow<FlowEvent<List<Song>, MessageProgress>> = songs(since)

        private fun songs(since: Instant?): Flow<FlowEvent<List<Song>, MessageProgress>> = flow {
            requests += since
            emit(FlowEvent.Progress(MessageProgress(ImportPhase.Fetching, progress = null)))
            failure?.let { emit(FlowEvent.Failure(it)) } ?: emit(FlowEvent.Success(found))
        }

        override fun findPlaylists(existingSongs: List<Song>): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>> = flow {
            playlistRequests++
        }
    }

    /**
     * Counts its scans, signals [started] as each one begins, holds it open until a [gate] send, then throws [failure] if [failNext]
     * is set, reports [scanFailure] if that's set, or else finds no songs.
     */
    private class GatedProvider(
        override val type: MediaProviderType = MediaProviderType.Shuttle
    ) : MediaProvider {

        val scans = AtomicInt(0)
        val stored = AtomicInt(0)
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
            emit(scanFailure?.let { message -> FlowEvent.Failure(message) } ?: FlowEvent.Success(emptyList()))
        }

        override suspend fun songsStored() {
            stored.incrementAndFetch()
        }

        override fun findPlaylists(existingSongs: List<Song>): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>> = emptyFlow()
    }

    private companion object {
        const val IMPORTS = 8
    }

    private fun song(
        id: Long = 1,
        path: String = "jellyfin://item/1"
    ) = Song(
        id = id,
        name = "Song",
        albumArtist = "Artist",
        artists = listOf("Artist"),
        album = "Album",
        track = 1,
        disc = 1,
        duration = 180_000,
        date = null,
        genres = emptyList(),
        path = path,
        size = 0,
        mimeType = "Audio/*",
        lastModified = null,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        mediaProvider = MediaProviderType.Jellyfin,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null,
        artworkVersion = null,
        dateAdded = null
    )

    private class FakeClock(var time: Instant) : Clock {
        override fun now(): Instant = time
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

    /** A repository whose queries all return [stored]; anything else fails the test. */
    private class FakeSongRepository : SongRepository {
        var stored: List<Song> = emptyList()

        /** The rows each [insertUpdateAndDelete] was asked to write. */
        val writes = mutableListOf<Int>()

        /** The songs each provider's imports deleted. */
        val deleted = mutableMapOf<MediaProviderType, List<Song>>()

        /** The inserts, updates and deletes of the last [insertUpdateAndDelete]. */
        var changes = Triple(0, 0, 0)

        override fun getSongs(query: SongQuery): Flow<List<Song>?> = flowOf(stored)

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
        ): Triple<Int, Int, Int> {
            writes += inserts.size + updates.size + deletes.size
            deleted[mediaProviderType] = deleted[mediaProviderType].orEmpty() + deletes
            return Triple(inserts.size, updates.size, deletes.size).also { changes = it }
        }

        override suspend fun remapPaths(remaps: List<SongPathRemap>, mediaProviderType: MediaProviderType): List<SongPathRemap> = notFaked()

        override suspend fun setPlaybackPosition(song: Song, playbackPosition: Int) = notFaked()

        override suspend fun recordPlayedThrough(song: Song) = notFaked()

        override suspend fun setExcluded(songs: List<Song>, excluded: Boolean) = notFaked()

        override suspend fun clearExcludeList() = notFaked()

        override suspend fun setFavourite(songs: List<Song>, favourite: Boolean) = notFaked()

        private fun notFaked(): Nothing = error("SongRepository call isn't faked")
    }
}
