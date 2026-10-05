package com.simplecityapps.mediaprovider

import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.mediaprovider.worker.MediaImportWorker
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.query.SongQuery
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MediaImportWorkerTest {
    private val provider = ScanProvider()
    private val preferences =
        GeneralPreferenceManager(InMemoryKeyValueStore()).apply {
            // The library has been imported under this build, so the periodic sync has something to do
            lastMediaImportDate = kotlin.time.Clock.System.now()
            songTagsRescanVersion = MediaImporter.SONG_TAGS_VERSION
        }
    private val importer =
        MediaImporter(
            strings = FakeStrings,
            songRepository = EmptySongRepository,
            playlistStore = object : ImportedPlaylistStore {
                override suspend fun storePlaylist(playlist: MediaImporter.PlaylistUpdateData) = Unit

                override suspend fun reconcilePlaylists(
                    type: MediaProviderType,
                    listing: MediaImporter.PlaylistListing,
                    listingComplete: Boolean
                ) = Unit
            },
            preferenceManager = preferences,
            afterImport = {}
        ).apply { mediaProviders += provider }

    private var foregrounds = 0

    private fun worker(attempt: Int = 0) = TestListenableWorkerBuilder<MediaImportWorker>(RuntimeEnvironment.getApplication())
        .setRunAttemptCount(attempt)
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
    fun `a sync that succeeds is a success`() = runBlocking<Unit> {
        worker().doWork() shouldBe Result.success()

        provider.scans shouldBe 1
    }

    @Test
    fun `a sync that fails is retried`() = runBlocking<Unit> {
        provider.failure = "Server unreachable"

        worker().doWork() shouldBe Result.retry()
    }

    @Test
    fun `a sync that fails again with the same error is retried again`() = runBlocking<Unit> {
        provider.failure = "Server unreachable"

        worker().doWork() shouldBe Result.retry()
        worker(attempt = 1).doWork() shouldBe Result.retry()
    }

    @Test
    fun `a sync that is skipped is a success and shows no notification`() = runBlocking<Unit> {
        preferences.lastMediaImportDate = null

        worker().doWork() shouldBe Result.success()

        provider.scans shouldBe 0
        foregrounds shouldBe 0
    }

    @Test
    fun `a sync that keeps failing gives up at the attempt cap`() = runBlocking<Unit> {
        provider.failure = "Server unreachable"

        worker(attempt = 2).doWork() shouldBe Result.success()
    }

    private class ScanProvider : MediaProvider {
        override val type = MediaProviderType.Shuttle
        var scans = 0
        var failure: String? = null

        override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = flow {
            scans++
            emit(failure?.let { FlowEvent.Failure(it) } ?: FlowEvent.Success(emptyList()))
        }

        override fun findPlaylists(existingSongs: List<Song>): Flow<FlowEvent<MediaImporter.PlaylistListing, MessageProgress>> = emptyFlow()
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
