package com.simplecityapps.localmediaprovider.local.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val SHARED_PATH = "/storage/emulated/0/Music/Song.mp3"

/**
 * The MediaStore and S2 (TagLib) providers both index the same file when both are enabled (#420): each keeps its own
 * row rather than fighting over `songs.path`'s unique index.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ConflictingProviderImportTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java).allowMainThreadQueries().build()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        database.close()
        scope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `importing the same path from two providers keeps one row per provider`() = runBlocking<Unit> {
        val importer =
            MediaImporter(
                context = context,
                songRepository = LocalSongRepository(scope, database.songDataDao()),
                playlistStore = LocalPlaylistRepository(context, scope, database.playlistDataDao(), database.playlistSongJoinDataDao(), database.songDataDao()),
                preferenceManager = GeneralPreferenceManager(context.getSharedPreferences("conflicting-import-test", Context.MODE_PRIVATE))
            )
        importer.mediaProviders += FakeProvider(MediaProviderType.MediaStore)
        importer.mediaProviders += FakeProvider(MediaProviderType.Shuttle)

        importer.import()

        val songs = database.songDataDao().get()
        songs.size shouldBe 2
        songs.forEach { song -> song.path shouldBe SHARED_PATH }
        songs.map { song -> song.mediaProvider } shouldContainExactlyInAnyOrder listOf(MediaProviderType.MediaStore, MediaProviderType.Shuttle)

        // A second import doesn't disturb either row
        importer.import()
        database.songDataDao().get().map { song -> song.id }.toSet() shouldBe songs.map { song -> song.id }.toSet()
    }

    private class FakeProvider(override val type: MediaProviderType) : MediaProvider {
        override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = flowOf(
            FlowEvent.Success(
                listOf(
                    Song(
                        id = 0,
                        name = "Song",
                        albumArtist = "Artist",
                        artists = listOf("Artist"),
                        album = "Album",
                        track = 1,
                        disc = 1,
                        duration = 180_000,
                        date = null,
                        genres = emptyList(),
                        path = SHARED_PATH,
                        size = 5_000,
                        mimeType = "audio/mpeg",
                        lastModified = Instant.fromEpochMilliseconds(1_700_000_000_000),
                        lastPlayed = null,
                        lastCompleted = null,
                        playCount = 0,
                        playbackPosition = 0,
                        blacklisted = false,
                        mediaProvider = type,
                        lyrics = null,
                        grouping = null,
                        bitRate = null,
                        bitDepth = null,
                        sampleRate = null,
                        channelCount = null
                    )
                )
            )
        )

        override fun findPlaylists(existingSongs: List<Song>): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>> = flowOf(FlowEvent.Success(emptyList()))
    }
}
