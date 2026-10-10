package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.SourceReachability
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MediaImporterSyncTest : MediaImporterTestBase() {
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
}
