package com.simplecityapps.mediaprovider

import com.simplecityapps.mediaprovider.MediaImporter.Companion.songTagsOutdated
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SourceReachability
import com.simplecityapps.shuttle.query.SongQuery
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
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
import kotlinx.coroutines.cancelAndJoin
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

    /** What each [MediaImporter] afterImport was told: whether every source's songs hold every tag. */
    private val afterImports = mutableListOf<Boolean>()
    private val importer =
        MediaImporter(
            strings = FakeMediaImportStrings,
            songRepository = songRepository,
            playlistStore = object : ImportedPlaylistStore {
                override suspend fun storePlaylist(playlist: MediaImporter.PlaylistUpdateData) = error("ImportedPlaylistStore.storePlaylist isn't faked")

                override suspend fun storedPlaylistIds(type: MediaProviderType): Set<String> = emptySet()

                override suspend fun reconcilePlaylists(
                    type: MediaProviderType,
                    listing: MediaImporter.PlaylistListing,
                    listingComplete: Boolean,
                    lastServerSongs: Map<String, Set<Long>>
                ) = error("ImportedPlaylistStore.reconcilePlaylists isn't faked")
            },
            preferenceManager = preferences,
            afterImport = { songTagsCurrent -> afterImports += songTagsCurrent },
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
    fun `each source's import ending counts as a completion - though the overall state is the same after the second`() = runBlocking<Unit> {
        val server = GatedProvider(MediaProviderType.Jellyfin)
        importer.mediaProviders += server
        provider.gate.trySend(Unit)
        server.gate.trySend(Unit)

        importer.import()

        importer.songImportState.value shouldBe SongImportState.ImportComplete(MediaProviderType.Shuttle, error = null)
        importer.importsCompleted.value shouldBe 2
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
    fun `an import that succeeds records the source as reachable and when it was updated`() = runBlocking<Unit> {
        provider.gate.trySend(Unit)

        importer.import()

        preferences.sourceReachability(provider.type.name) shouldBe SourceReachability(error = null, checkedAt = clock.time)
        preferences.sourceUpdated(provider.type.name) shouldBe clock.time
    }

    @Test
    fun `an import whose source reports a failure records its message and leaves the updated time alone`() = runBlocking<Unit> {
        val updated = clock.time - 1.days
        preferences.setSourceUpdated(provider.type.name, updated)
        provider.scanFailure = "Server unreachable"
        provider.gate.trySend(Unit)

        importer.import()

        preferences.sourceReachability(provider.type.name) shouldBe SourceReachability(error = "Server unreachable", checkedAt = clock.time)
        preferences.sourceUpdated(provider.type.name) shouldBe updated
    }

    @Test
    fun `an import that throws records the failure and leaves the updated time alone`() = runBlocking<Unit> {
        val updated = clock.time - 1.days
        preferences.setSourceUpdated(provider.type.name, updated)
        provider.failNext.store(true)
        provider.gate.trySend(Unit)

        runCatching { importer.import() }

        preferences.sourceReachability(provider.type.name) shouldBe SourceReachability(error = "Import failed", checkedAt = clock.time)
        preferences.sourceUpdated(provider.type.name) shouldBe updated
    }

    @Test
    fun `a quiet sync that fails records nothing`() = runBlocking<Unit> {
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        server.failure = "Unreachable"

        importer.sync(SyncTrigger.Foreground)

        preferences.sourceReachability(server.type.name) shouldBe null
        preferences.sourceUpdated(server.type.name) shouldBe null
    }

    @Test
    fun `a quiet sync that succeeds clears an error stored earlier`() = runBlocking<Unit> {
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        preferences.setSourceReachability(server.type.name, SourceReachability(error = "Unreachable", checkedAt = clock.time - 1.hours))

        importer.sync(SyncTrigger.Foreground)

        preferences.sourceReachability(server.type.name) shouldBe SourceReachability(error = null, checkedAt = clock.time)
        preferences.sourceUpdated(server.type.name) shouldBe clock.time
    }

    @Test
    fun `a sync where every source succeeds reports each as a success and says it started`() = runBlocking<Unit> {
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        var started = 0

        val result = importer.sync(SyncTrigger.Periodic, onStart = { started++ })

        result shouldBe SyncResult.Ran(mapOf(MediaProviderType.Jellyfin to ProviderSyncOutcome.Success))
        result.shouldRetry shouldBe false
        started shouldBe 1
    }

    @Test
    fun `a sync that is skipped says so and never says it started`() = runBlocking<Unit> {
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        preferences.lastMediaImportDate = null
        var started = 0

        val result = importer.sync(SyncTrigger.Periodic, onStart = { started++ })

        result shouldBe SyncResult.Skipped
        result.shouldRetry shouldBe false
        started shouldBe 0
    }

    @Test
    fun `a sync where every source was synced recently never says it started`() = runBlocking<Unit> {
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        preferences.setSongTagsVersion(server.type.name, MediaImporter.SONG_TAGS_VERSION)
        preferences.setLastSyncStart(server.type.name, clock.time - 1.minutes)
        preferences.setLastFullSyncStart(server.type.name, clock.time - 1.minutes)
        var started = 0

        val result = importer.sync(SyncTrigger.Foreground, onStart = { started++ })

        result shouldBe SyncResult.Skipped
        started shouldBe 0
    }

    @Test
    fun `a sync cancelled mid-way rethrows the cancellation rather than reporting it aborted`() = runBlocking<Unit> {
        val outcome = CompletableDeferred<Result<SyncResult>>()
        val run = launch(Dispatchers.Default) { outcome.complete(runCatching { importer.sync(SyncTrigger.Periodic) }) }
        provider.started.receive()

        run.cancelAndJoin()

        outcome.await().exceptionOrNull().shouldBeInstanceOf<kotlin.coroutines.cancellation.CancellationException>()
    }

    @Test
    fun `an import requested during a sync follows it and its failure makes the sync worth retrying`() = runBlocking<Unit> {
        val result = CompletableDeferred<SyncResult>()
        var started = 0
        launch(Dispatchers.Default) { result.complete(importer.sync(SyncTrigger.Periodic, onStart = { started++ })) }
        provider.started.receive()

        importer.import() // requested while the sync runs: left for the sync to run after it
        provider.gate.trySend(Unit)
        provider.started.receive() // the follow-up pass starts
        provider.failNext.store(true)
        provider.gate.trySend(Unit)

        result.await() shouldBe SyncResult.Aborted(provider.failure)
        result.await().shouldRetry shouldBe true
        started shouldBe 1
    }

    @Test
    fun `a sync where one source fails and another succeeds reports both and is worth retrying`() = runBlocking<Unit> {
        importer.mediaProviders += server
        server.failure = "Unreachable"
        provider.gate.trySend(Unit)

        val result = importer.sync(SyncTrigger.Periodic)

        result shouldBe
            SyncResult.Ran(
                mapOf(
                    MediaProviderType.Shuttle to ProviderSyncOutcome.Success,
                    MediaProviderType.Jellyfin to ProviderSyncOutcome.Failed("Unreachable")
                )
            )
        result.shouldRetry shouldBe true
    }

    @Test
    fun `a source that throws during a sync is a failure carrying what it threw`() = runBlocking<Unit> {
        provider.failNext.store(true)
        provider.gate.trySend(Unit)

        val result = importer.sync(SyncTrigger.Periodic)

        result shouldBe SyncResult.Ran(mapOf(MediaProviderType.Shuttle to ProviderSyncOutcome.Failed("Import failed", cause = provider.failure)))
        result.shouldRetry shouldBe true
    }

    @Test
    fun `a source removed during a sync is cancelled and not worth retrying`() = runBlocking<Unit> {
        val removed = GatedProvider(MediaProviderType.Jellyfin)
        importer.mediaProviders += removed
        val result = CompletableDeferred<SyncResult>()
        launch(Dispatchers.Default) { result.complete(importer.sync(SyncTrigger.Periodic)) }
        provider.started.receive()
        removed.started.receive()

        importer.removeProvider(removed)
        provider.gate.trySend(Unit)

        result.await() shouldBe
            SyncResult.Ran(
                mapOf(
                    MediaProviderType.Shuttle to ProviderSyncOutcome.Success,
                    MediaProviderType.Jellyfin to ProviderSyncOutcome.Cancelled
                )
            )
        result.await().shouldRetry shouldBe false
    }

    @Test
    fun `an import cancelled mid-way records nothing`() = runBlocking<Unit> {
        val import = launch(Dispatchers.Default) { importer.import() }
        provider.started.receive()

        import.cancelAndJoin()

        preferences.sourceReachability(provider.type.name) shouldBe null
        preferences.sourceUpdated(provider.type.name) shouldBe null
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
    fun `a sync on return to the app asks a server only for what changed since its last sync - and leaves this device alone`() = runBlocking<Unit> {
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
    fun `a sync shows no progress - which would replace the library with the scanning state - only how it ended`() = runBlocking<Unit> {
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
    fun `a sync that stored a change says so - so what shows the library reloads`() = runBlocking<Unit> {
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
    fun `an import the user asks for looks past a source's index - a scheduled sync trusts it`() = runBlocking<Unit> {
        val indexed = IndexedProvider()
        importer.mediaProviders -= provider
        importer.mediaProviders += indexed

        // A folder change, a rescan, then the daily sync
        importer.import(foldersChanged = true)
        importer.import()
        importer.sync(SyncTrigger.Periodic)

        indexed.listings shouldBe listOf("thorough", "thorough", "index")
    }

    @Test
    fun `a source without an index records its unread files too`() = runBlocking<Unit> {
        provider.skippedFiles = setOf("/music/a.flac")
        provider.gate.trySend(Unit)
        importer.import()

        preferences.skippedFiles(provider.type.name) shouldBe 1
    }

    @Test
    fun `a full import records how many files the source left unread`() = runBlocking<Unit> {
        val indexed = IndexedProvider()
        importer.mediaProviders -= provider
        importer.mediaProviders += indexed

        indexed.skippedFiles = setOf("/music/a.flac", "/music/b.flac")
        importer.import()
        preferences.skippedFiles(indexed.type.name) shouldBe 2

        indexed.skippedFiles = emptySet()
        importer.import()
        preferences.skippedFiles(indexed.type.name) shouldBe 0
    }

    @Test
    fun `a server that can't be reached on return to the app keeps its status - but the daily sync reports it`() = runBlocking<Unit> {
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
    fun `a sync waits for the first import - and for the one a build with new tags is due`() = runBlocking<Unit> {
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
    fun `a listing that came up short deletes nothing and makes the next sync full - which deletes as usual`() = runBlocking<Unit> {
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        songRepository.stored = listOf(song())
        server.found = listOf(song().copy(id = 2, path = "/added"))
        server.missing = 1

        importer.sync(SyncTrigger.Periodic)

        songRepository.changes shouldBe Triple(1, 0, 0)
        preferences.lastSyncStart(server.type.name) shouldBe clock.time
        preferences.lastFullSyncStart(server.type.name) shouldBe null
        preferences.songTagsOutdated(server.type) shouldBe true

        server.missing = 0
        importer.sync(SyncTrigger.Periodic)

        server.requests shouldBe listOf(null, null)
        songRepository.changes shouldBe Triple(1, 0, 1)
        preferences.lastFullSyncStart(server.type.name) shouldBe clock.time
        preferences.songTagsOutdated(server.type) shouldBe false
    }

    @Test
    fun `a server short by as many songs every full sync deletes from the second - and keeps its full-sync time`() = runBlocking<Unit> {
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        songRepository.stored = listOf(song())
        server.found = listOf(song().copy(id = 2, path = "/added"))
        server.missing = 2

        importer.sync(SyncTrigger.Periodic)

        songRepository.changes shouldBe Triple(1, 0, 0)
        preferences.lastFullSyncStart(server.type.name) shouldBe null

        importer.sync(SyncTrigger.Periodic)

        server.requests shouldBe listOf(null, null)
        songRepository.changes shouldBe Triple(1, 0, 1)
        preferences.lastFullSyncStart(server.type.name) shouldBe clock.time
        preferences.songTagsOutdated(server.type) shouldBe false
    }

    @Test
    fun `an incremental sync that came up short leaves the next full sync where it was`() = runBlocking<Unit> {
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        songRepository.stored = listOf(song())
        preferences.setSongTagsVersion(server.type.name, MediaImporter.SONG_TAGS_VERSION)
        preferences.setLastSyncStart(server.type.name, clock.time - 1.hours)
        preferences.setLastFullSyncStart(server.type.name, clock.time - 1.days)
        server.found = listOf(song().copy(id = 2, path = "/added"))
        server.missing = 1

        importer.sync(SyncTrigger.Foreground)

        server.requests shouldBe listOf(clock.time - 1.hours - SyncPolicy.OVERLAP)
        preferences.lastSyncStart(server.type.name) shouldBe clock.time
        preferences.lastFullSyncStart(server.type.name) shouldBe clock.time - 1.days
    }

    /** A server with songs 1 to 3 stored, last synced an hour ago and in full a day ago, that an incremental sync finds nothing new on. */
    private suspend fun incrementalServer() {
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        songRepository.stored = listOf(song(1, "/1"), song(2, "/2"), song(3, "/3"))
        preferences.setSongTagsVersion(server.type.name, MediaImporter.SONG_TAGS_VERSION)
        preferences.setLastSyncStart(server.type.name, clock.time - 1.hours)
        preferences.setLastFullSyncStart(server.type.name, clock.time - 1.days)
    }

    @Test
    fun `an incremental sync removes the songs the server no longer lists`() = runBlocking<Unit> {
        incrementalServer()
        server.count = 2
        server.held = listOf("/1", "/3")

        importer.sync(SyncTrigger.Foreground)

        songRepository.deleted[server.type]?.map { it.path } shouldBe listOf("/2")
        preferences.lastSyncStart(server.type.name) shouldBe clock.time
        preferences.lastFullSyncStart(server.type.name) shouldBe clock.time - 1.days
    }

    @Test
    fun `an incremental sync whose server count is what is stored lists nothing`() = runBlocking<Unit> {
        incrementalServer()
        server.count = 3

        importer.sync(SyncTrigger.Foreground)

        server.pathListings shouldBe 0
        songRepository.deleted[server.type].orEmpty() shouldBe emptyList()
    }

    @Test
    fun `an incremental sync counts the songs it added as held`() = runBlocking<Unit> {
        incrementalServer()
        server.found = listOf(song(4, "/4"))
        server.count = 4

        importer.sync(SyncTrigger.Foreground)

        server.pathListings shouldBe 0
        songRepository.changes shouldBe Triple(1, 0, 0)
    }

    @Test
    fun `an incremental sync adding a song while another is gone still removes it`() = runBlocking<Unit> {
        incrementalServer()
        server.found = listOf(song(4, "/4"))
        server.count = 3
        server.held = listOf("/1", "/2", "/4")

        importer.sync(SyncTrigger.Foreground)

        songRepository.changes shouldBe Triple(1, 0, 1)
        songRepository.deleted[server.type]?.map { it.path } shouldBe listOf("/3")
    }

    @Test
    fun `an incremental sync with a count it cannot read lists the songs`() = runBlocking<Unit> {
        incrementalServer()
        server.count = null
        server.held = listOf("/1", "/2")

        importer.sync(SyncTrigger.Foreground)

        server.pathListings shouldBe 1
        songRepository.deleted[server.type]?.map { it.path } shouldBe listOf("/3")
    }

    @Test
    fun `an incremental sync whose path listing fails removes nothing - and still stores what it found`() = runBlocking<Unit> {
        incrementalServer()
        server.found = listOf(song(4, "/4"))
        server.count = 2
        server.held = null

        importer.sync(SyncTrigger.Foreground)

        songRepository.changes shouldBe Triple(1, 0, 0)
        preferences.lastSyncStart(server.type.name) shouldBe clock.time
    }

    @Test
    fun `an incremental sync whose path listing came up short removes nothing`() = runBlocking<Unit> {
        incrementalServer()
        server.count = 2
        server.held = listOf("/1")
        server.heldMissing = 1

        importer.sync(SyncTrigger.Foreground)

        songRepository.deleted[server.type].orEmpty() shouldBe emptyList()
    }

    @Test
    fun `an incremental sync that would remove every song holds the removal back`() = runBlocking<Unit> {
        incrementalServer()
        server.count = 0
        server.held = emptyList()

        importer.sync(SyncTrigger.Foreground)

        songRepository.deleted[server.type].orEmpty() shouldBe emptyList()
    }

    @Test
    fun `a server with nothing stored is synced in full - whenever it last synced`() = runBlocking<Unit> {
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
    fun `an import after the user changed folders removes this device's songs at once - but keeps those under an unreadable root and a server's`() = runBlocking<Unit> {
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
    fun `a full sync that holds back a mass removal makes the next sync full - to apply it`() = runBlocking<Unit> {
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

    @Test
    fun `a source added while an import runs is read by the follow-up pass - and changing the sources never disturbs one`() = runBlocking<Unit> {
        val import = launch(Dispatchers.Default) { importer.import() }
        provider.started.receive()

        // Sources changing from another thread while the import reads them, and while each pass's end reads them again
        val churn = launch(Dispatchers.Default) {
            repeat(CHURN) { i ->
                val other = ServerProvider(if (i % 2 == 0) MediaProviderType.Emby else MediaProviderType.Plex)
                importer.mediaProviders += other
                importer.mediaProviders.any { it.type.remote }
                importer.mediaProviders -= other
            }
        }
        val added = GatedProvider(MediaProviderType.Jellyfin)
        importer.mediaProviders += added
        importer.import() // requested while the first pass runs

        provider.gate.trySend(Unit)
        provider.started.receive()
        added.started.receive()
        provider.gate.trySend(Unit)
        added.gate.trySend(Unit)
        import.join()
        churn.join()

        provider.scans.load() shouldBe 2
        added.scans.load() shouldBe 1
        importer.mediaProviders shouldBe setOf(provider, added)
    }

    @Test
    fun `a source that throws leaves the others to finish - and says how it ended rather than staying in progress`() = runBlocking<Unit> {
        val server = GatedProvider(MediaProviderType.Jellyfin)
        importer.mediaProviders += server
        val result = CompletableDeferred<Result<Unit>>()
        launch(Dispatchers.Default) { result.complete(runCatching { importer.import() }) }
        provider.started.receive()
        server.started.receive()

        provider.failNext.store(true)
        provider.gate.trySend(Unit)
        while (importer.providerImportStates.value[MediaProviderType.Shuttle] !is SongImportState.ImportComplete) yield()
        server.gate.trySend(Unit)

        result.await().exceptionOrNull() shouldBe provider.failure
        server.stored.load() shouldBe 1
        importer.providerImportStates.value shouldBe mapOf(
            MediaProviderType.Shuttle to SongImportState.ImportComplete(MediaProviderType.Shuttle, "Import failed"),
            MediaProviderType.Jellyfin to SongImportState.ImportComplete(MediaProviderType.Jellyfin, error = null),
        )
        importer.songImportState.value shouldBe SongImportState.ImportComplete(MediaProviderType.Shuttle, "Import failed")
    }

    @Test
    fun `a source that throws still lets what the others stored count as imported`() = runBlocking<Unit> {
        val server = GatedProvider(MediaProviderType.Jellyfin)
        importer.mediaProviders += server
        preferences.lastMediaImportDate = null
        provider.failNext.store(true)
        provider.gate.trySend(Unit)
        server.gate.trySend(Unit)

        runCatching { importer.import() }.exceptionOrNull() shouldBe provider.failure

        preferences.lastMediaImportDate shouldBe clock.time
        // The failing source stays outdated, so the album key move still waits on it
        afterImports shouldBe listOf(false)
    }

    @Test
    fun `an import where every source throws isn't recorded`() = runBlocking<Unit> {
        preferences.lastMediaImportDate = null
        provider.failNext.store(true)
        provider.gate.trySend(Unit)

        runCatching { importer.import() }.exceptionOrNull() shouldBe provider.failure

        preferences.lastMediaImportDate shouldBe null
        afterImports.shouldBeEmpty()
    }

    @Test
    fun `the overall progress is every running source's - not whichever reported last`() = runBlocking<Unit> {
        val server = GatedProvider(MediaProviderType.Jellyfin)
        importer.mediaProviders += server
        val import = launch(Dispatchers.Default) { importer.import() }
        provider.started.receive()
        server.started.receive()

        server.gate.trySend(Unit)
        while (importer.providerImportStates.value[MediaProviderType.Jellyfin] !is SongImportState.ImportComplete) yield()

        // The server finished last, but this device's import still runs
        importer.songImportState.value shouldBe SongImportState.ImportProgress(MediaProviderType.Shuttle, "Fetching", progress = null)

        provider.gate.trySend(Unit)
        import.join()

        importer.songImportState.value shouldBe SongImportState.ImportComplete(MediaProviderType.Shuttle, error = null)
    }

    @Test
    fun `a cancelled import reports no completion - and its source goes back to how it stood`() = runBlocking<Unit> {
        provider.scanFailure = "Server unreachable"
        provider.gate.trySend(Unit)
        importer.import()
        provider.started.receive() // the first import's
        val completions = importer.importsCompleted.value

        provider.scanFailure = null
        val import = launch(Dispatchers.Default) { importer.import() }
        provider.started.receive()
        importer.songImportState.value.shouldBeInstanceOf<SongImportState.ImportProgress>()
        import.cancelAndJoin()

        importer.importsCompleted.value shouldBe completions
        importer.songImportState.value shouldBe SongImportState.ImportComplete(MediaProviderType.Shuttle, "Server unreachable")
    }

    @Test
    fun `a cancelled first import leaves the source with no state rather than scanning`() = runBlocking<Unit> {
        val import = launch(Dispatchers.Default) { importer.import() }
        provider.started.receive()
        import.cancelAndJoin()

        importer.importsCompleted.value shouldBe 0
        importer.providerImportStates.value shouldBe emptyMap()
        importer.songImportState.value shouldBe SongImportState.Idle
    }

    @Test
    fun `a source removed while its import runs stores nothing after - and the others finish`() = runBlocking<Unit> {
        val server = GatedProvider(MediaProviderType.Jellyfin).apply { found = listOf(song()) }
        importer.mediaProviders += server
        val import = launch(Dispatchers.Default) { importer.import() }
        provider.started.receive()
        server.started.receive()

        importer.removeProvider(server)
        server.gate.trySend(Unit) // what it would have found arrives too late

        provider.gate.trySend(Unit)
        import.join()

        songRepository.writes.sum() shouldBe 0
        server.stored.load() shouldBe 0
        provider.stored.load() shouldBe 1
        importer.mediaProviders shouldBe setOf(provider)
        importer.providerImportStates.value shouldBe mapOf(MediaProviderType.Shuttle to SongImportState.ImportComplete(MediaProviderType.Shuttle, error = null))
    }

    /** An importer of [server] alone. */
    private fun serverImporter() = MediaImporter(
        strings = FakeMediaImportStrings,
        songRepository = songRepository,
        playlistStore = object : ImportedPlaylistStore {
            override suspend fun storePlaylist(playlist: MediaImporter.PlaylistUpdateData) = error("ImportedPlaylistStore.storePlaylist isn't faked")

            override suspend fun storedPlaylistIds(type: MediaProviderType): Set<String> = emptySet()

            override suspend fun reconcilePlaylists(
                type: MediaProviderType,
                listing: MediaImporter.PlaylistListing,
                listingComplete: Boolean,
                lastServerSongs: Map<String, Set<Long>>
            ) = error("ImportedPlaylistStore.reconcilePlaylists isn't faked")
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

        /** How many songs short of its total its listings come to ([FlowEvent.Success.missing]). */
        var missing = 0

        override var unreadableRoots: Set<String> = emptySet()

        /** How many songs it says it holds, or null for a count it can't read. */
        var count: Int? = null

        /** The paths it lists as held (missing [heldMissing] short of its total), or null for a path listing that fails. */
        var held: List<String>? = null
        var heldMissing = 0
        var pathListings = 0

        override suspend fun countSongs(): Int? = count

        override fun findSongPaths(): Flow<FlowEvent<List<String>, MessageProgress>> = flow {
            pathListings++
            held?.let { paths -> emit(FlowEvent.Success(paths, heldMissing)) } ?: emit(FlowEvent.Failure("The listing failed"))
        }

        override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = songs(since = null)

        override fun findSongsChangedSince(
            existingSongs: List<Song>,
            since: Instant
        ): Flow<FlowEvent<List<Song>, MessageProgress>> = songs(since)

        private fun songs(since: Instant?): Flow<FlowEvent<List<Song>, MessageProgress>> = flow {
            requests += since
            emit(FlowEvent.Progress(MessageProgress(ImportPhase.Fetching, progress = null)))
            failure?.let { emit(FlowEvent.Failure(it)) } ?: emit(FlowEvent.Success(found, missing))
        }

        override fun findPlaylists(existingSongs: List<Song>, knownVersions: Map<String, String>): Flow<FlowEvent<MediaImporter.PlaylistListing, MessageProgress>> = flow {
            playlistRequests++
        }
    }

    /** A source with an index, which records how each of its listings was asked for. */
    private class IndexedProvider : IndexedMediaProvider {
        override val type = MediaProviderType.Shuttle

        val listings = mutableListOf<String>()

        override var skippedFiles: Set<String> = emptySet()

        override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = listing("index")

        override fun findSongsThoroughly(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = listing("thorough")

        private fun listing(kind: String): Flow<FlowEvent<List<Song>, MessageProgress>> = flow {
            listings += kind
            emit(FlowEvent.Success(emptyList()))
        }

        override fun findPlaylists(existingSongs: List<Song>, knownVersions: Map<String, String>): Flow<FlowEvent<MediaImporter.PlaylistListing, MessageProgress>> = emptyFlow()
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

        /** The files its last listing left unread, as a source without an index (MediaStore) reports them. */
        @Volatile override var skippedFiles: Set<String> = emptySet()

        /** What it finds, once its gate opens. */
        @Volatile var found: List<Song> = emptyList()

        override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = flow {
            scans.incrementAndFetch()
            started.send(Unit)
            gate.receive()
            if (failNext.exchange(false)) throw failure
            emit(scanFailure?.let { message -> FlowEvent.Failure(message) } ?: FlowEvent.Success(found))
        }

        override suspend fun songsStored() {
            stored.incrementAndFetch()
        }

        override fun findPlaylists(existingSongs: List<Song>, knownVersions: Map<String, String>): Flow<FlowEvent<MediaImporter.PlaylistListing, MessageProgress>> = emptyFlow()
    }

    private companion object {
        const val IMPORTS = 8
        const val CHURN = 10_000
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

        override fun countSongs(): Flow<Int> = flowOf(stored.size)

        override suspend fun loadLyrics(songId: Long): String? = null

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
