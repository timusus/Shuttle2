package com.simplecityapps.mediaprovider

import androidx.work.ListenableWorker.Result
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.mediaprovider.worker.MediaImportWorker
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SourceStateStore
import com.simplecityapps.shuttle.query.SongQuery
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MediaImportWorkerTest {
    private val provider = ScanProvider()

    /** What the importer's afterImport throws, which fails the sync itself rather than one source. */
    private var afterImportFailure: Exception? = null
    private val preferences =
        SourceStateStore(InMemoryKeyValueStore()).apply {
            // The library has been imported under this build, so the periodic sync has something to do
            lastMediaImportDate = kotlin.time.Clock.System.now()
            songTagsRescanVersion = SONG_TAGS_VERSION
        }
    private val importer =
        MediaImporter(
            strings = FakeStrings,
            songRepository = EmptySongRepository,
            playlistStore = object : ImportedPlaylistStore {
                override suspend fun storePlaylist(playlist: PlaylistUpdateData) = Unit

                override suspend fun storedPlaylistIds(type: MediaProviderType): Set<String> = emptySet()

                override suspend fun reconcilePlaylists(
                    type: MediaProviderType,
                    listing: PlaylistListing,
                    listingComplete: Boolean,
                    lastServerSongs: Map<String, Set<Long>>
                ) = Unit
            },
            sourceState = preferences,
            afterImport = { afterImportFailure?.let { throw it } }
        ).apply { mediaProviders += provider }

    private var foregrounds = 0

    private fun worker(
        attempt: Int = 0,
        fullImport: Boolean = false
    ) = TestListenableWorkerBuilder<MediaImportWorker>(RuntimeEnvironment.getApplication())
        .setRunAttemptCount(attempt)
        .setInputData(workDataOf(MediaImportWorker.KEY_FULL_IMPORT to fullImport))
        .setForegroundUpdater { _, _, _ ->
            foregrounds++
            com.google.common.util.concurrent.Futures.immediateVoidFuture()
        }
        .setWorkerFactory(
            object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: android.content.Context,
                    workerClassName: String,
                    workerParameters: androidx.work.WorkerParameters
                ) = MediaImportWorker(appContext, workerParameters, importer)
            }
        )
        .build()

    @Test
    fun `a sync that succeeds is a success`() = runTest {
        worker().doWork() shouldBe Result.success()

        provider.scans shouldBe 1
    }

    @Test
    fun `a periodic sync never goes to the foreground`() = runTest {
        worker().doWork() shouldBe Result.success()

        provider.scans shouldBe 1
        foregrounds shouldBe 0
    }

    @Test
    fun `a sync where only one of two sources fails is retried`() = runTest {
        importer.mediaProviders += ScanProvider(MediaProviderType.Jellyfin)
        provider.failure = "Server unreachable"

        worker().doWork() shouldBe Result.retry()
    }

    @Test
    fun `a sync where every source succeeds is a success`() = runTest {
        val other = ScanProvider(MediaProviderType.Jellyfin)
        importer.mediaProviders += other

        worker().doWork() shouldBe Result.success()

        provider.scans shouldBe 1
        other.scans shouldBe 1
    }

    @Test
    fun `a cancelled sync is cancelled, not retried`() = runTest {
        provider.hang = true
        val run = launch { worker().doWork() }
        provider.started.receive()

        run.cancelAndJoin()

        run.isCancelled shouldBe true
    }

    @Test
    fun `a sync that throws is retried`() = runTest {
        afterImportFailure = IllegalStateException("afterImport failed")

        worker().doWork() shouldBe Result.retry()
    }

    @Test
    fun `a sync skipped before the first import is a success even after an earlier failure`() = runTest {
        provider.failure = "Server unreachable"
        worker().doWork() shouldBe Result.retry()

        preferences.lastMediaImportDate = null

        worker(attempt = 1).doWork() shouldBe Result.success()
    }

    @Test
    fun `a sync that fails is retried`() = runTest {
        provider.failure = "Server unreachable"

        worker().doWork() shouldBe Result.retry()
    }

    @Test
    fun `a sync that fails again with the same error is retried again`() = runTest {
        provider.failure = "Server unreachable"

        worker().doWork() shouldBe Result.retry()
        worker(attempt = 1).doWork() shouldBe Result.retry()
    }

    @Test
    fun `a sync that is skipped is a success and shows no notification`() = runTest {
        preferences.lastMediaImportDate = null

        worker().doWork() shouldBe Result.success()

        provider.scans shouldBe 0
        foregrounds shouldBe 0
    }

    @Test
    fun `a sync that keeps failing gives up at the attempt cap`() = runTest {
        provider.failure = "Server unreachable"

        worker(attempt = 2).doWork() shouldBe Result.success()
    }

    @Test
    fun `a requested import runs in the foreground even before the first import`() = runTest {
        preferences.lastMediaImportDate = null

        worker(fullImport = true).doWork() shouldBe Result.success()

        provider.scans shouldBe 1
        foregrounds shouldBe 1
    }

    @Test
    fun `a requested import that fails is not retried`() = runTest {
        afterImportFailure = IllegalStateException("afterImport failed")

        worker(fullImport = true).doWork() shouldBe Result.failure()
    }

    @Test
    fun `a requested import with nothing to import shows no notification`() = runTest {
        importer.mediaProviders.clear()

        worker(fullImport = true).doWork() shouldBe Result.success()

        foregrounds shouldBe 0
    }

    @Test
    fun `a requested import waits for the import already running`() = runTest {
        provider.hold = CompletableDeferred()
        val running = launch { importer.import() }
        provider.started.receive()
        val requested = async { worker(fullImport = true).doWork() }

        withTimeoutOrNull(200) { requested.await() } shouldBe null

        provider.hold!!.complete(Unit)
        requested.await() shouldBe Result.success()
        running.join()
        importer.isImporting shouldBe false
    }

    @Test
    fun `a second request while one is enqueued leaves one unique work chain`() {
        val context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        MediaImportWorker.enqueueFullImport(context, importer, foldersChanged = false)
        MediaImportWorker.enqueueFullImport(context, importer, foldersChanged = true)

        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("MEDIA_FULL_IMPORT").get()
        infos.size shouldBe 1
        infos.single().tags.contains("MEDIA_FULL_IMPORT") shouldBe true
    }

    private class ScanProvider(
        override val type: MediaProviderType = MediaProviderType.Shuttle
    ) : MediaProvider {
        var scans = 0
        var failure: String? = null

        /** Holds the scan open until cancelled, signalling [started] as it begins. */
        var hang = false

        /** Holds the scan open until completed. */
        var hold: CompletableDeferred<Unit>? = null
        val started = Channel<Unit>(Channel.UNLIMITED)

        override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = flow {
            scans++
            started.send(Unit)
            if (hang) awaitCancellation()
            hold?.await()
            emit(failure?.let { FlowEvent.Failure(it) } ?: FlowEvent.Success(emptyList()))
        }

        override fun findPlaylists(existingSongs: List<Song>, knownVersions: Map<String, String>): Flow<FlowEvent<PlaylistListing, MessageProgress>> = emptyFlow()
    }

    private object FakeStrings : MediaImportStrings {
        override fun connecting(provider: String) = "Connecting to $provider"
        override val fetching = "Fetching"
        override fun fetchingSongs(
            count: Int,
            total: Int
        ) = "Fetching $count of $total"
        override fun saving(count: Int) = "Saving $count"
        override val importError = "Import failed"
    }

    private object EmptySongRepository : SongRepository {
        override fun getSongs(query: SongQuery): Flow<List<Song>?> = flowOf(emptyList())
        override fun countSongs(): Flow<Int> = flowOf(0)

        override fun countSongsByProvider(): Flow<Map<MediaProviderType, Int>> = flowOf(emptyMap())

        override suspend fun loadLyrics(songId: Long): String? = null
        override val updatedSongIds: Flow<Set<Long>> = flowOf(emptySet())
        override suspend fun insert(songs: List<Song>, mediaProviderType: MediaProviderType) = unused()
        override suspend fun update(song: Song): Int = unused()
        override suspend fun update(songs: List<Song>) = unused()
        override suspend fun remove(song: Song) = unused()
        override suspend fun removeAll(mediaProviderType: MediaProviderType) = unused()
        override suspend fun insertUpdateAndDelete(
            inserts: List<Song>,
            updates: List<Song>,
            deletes: List<Song>,
            mediaProviderType: MediaProviderType
        ): Triple<Int, Int, Int> = Triple(inserts.size, updates.size, deletes.size)
        override suspend fun remapPaths(remaps: List<SongPathRemap>, mediaProviderType: MediaProviderType): List<SongPathRemap> = unused()
        override suspend fun setPlaybackPosition(song: Song, playbackPosition: Int) = unused()
        override suspend fun recordPlayedThrough(song: Song) = unused()
        override suspend fun setExcluded(songs: List<Song>, excluded: Boolean) = unused()
        override suspend fun clearExcludeList() = unused()
        override suspend fun setFavourite(songs: List<Song>, favourite: Boolean) = unused()

        private fun unused(): Nothing = error("SongRepository call isn't faked")
    }
}
