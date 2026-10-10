package com.simplecityapps.mediaprovider

import com.simplecityapps.mediaprovider.MediaImporter.Companion.songTagsOutdated
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.SourceReachability
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class MediaImporterImportTest : MediaImporterTestBase() {
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
    fun `an import that finds the stored songs unchanged writes no rows`() = runBlocking<Unit> {
        importer.mediaProviders -= provider
        importer.mediaProviders += server
        songRepository.stored = listOf(song(), song().copy(id = 2, path = "jellyfin://item/2"))
        server.found = songRepository.stored.map { it.copy(id = 0) }

        importer.import()

        songRepository.writes shouldBe listOf(0)
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

    private companion object {
        const val IMPORTS = 8
        const val CHURN = 10_000
    }
}
