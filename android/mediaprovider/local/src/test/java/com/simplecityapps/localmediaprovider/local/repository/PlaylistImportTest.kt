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
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.query.SongQuery
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Importing a provider's playlists with the real repositories, as [MediaImporter] runs it after the songs are stored. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PlaylistImportTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java).allowMainThreadQueries().build()
    private val songRepository = LaggingSongRepository(LocalSongRepository(scope, database.songDataDao()))
    private val playlistRepository = LocalPlaylistRepository(context, scope, database.playlistDataDao(), database.playlistSongJoinDataDao(), database.songDataDao())
    private val provider = FakeProvider()
    private val importer =
        MediaImporter(
            context = context,
            songRepository = songRepository,
            playlistRepository = playlistRepository,
            preferenceManager = GeneralPreferenceManager(context.getSharedPreferences("playlist-import-test", Context.MODE_PRIVATE))
        ).apply { mediaProviders += provider }

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
    fun `the first import creates the playlist even while the shared song list hasn't caught up with the new songs`() = runBlocking<Unit> {
        provider.songPaths = listOf(A, B, C)
        provider.playlist = listOf(A, B, C)

        importer.import()

        importedPlaylistPaths() shouldBe listOf(A, B, C)
    }

    /** The paths of the songs in the playlist imported from [PLAYLIST_ID], in order, read straight from the database. */
    private suspend fun importedPlaylistPaths(): List<String>? {
        val playlist = database.playlistDataDao().getAll().first().singleOrNull { playlist -> playlist.externalId == PLAYLIST_ID } ?: return null
        return database.playlistSongJoinDataDao().getSongsForPlaylist(playlist.id).first().map { playlistSong -> playlistSong.song.path }
    }

    /**
     * The songs [delegate] stores, except that [getSongs] still serves the empty library from before the import. The local
     * repository shares one song list across its collectors, and requeries it only after a write has returned, which takes a
     * while for a large library, so a read straight after the import can see the library as it was.
     */
    private class LaggingSongRepository(private val delegate: SongRepository) : SongRepository by delegate {
        override fun getSongs(query: SongQuery): Flow<List<Song>?> = flowOf(emptyList())
    }

    /** A local provider that finds a song at each of [songPaths] and one m3u playlist, [PLAYLIST_ID], listing [playlist]. */
    private class FakeProvider : MediaProvider {
        override val type = MediaProviderType.Shuttle

        @Volatile var songPaths: List<String> = emptyList()

        @Volatile var playlist: List<String> = emptyList()

        override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = flowOf(FlowEvent.Success(songPaths.map(::song)))

        override fun findPlaylists(
            existingPlaylists: List<Playlist>,
            existingSongs: List<Song>
        ): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>> = flow {
            val songs = playlist.mapNotNull { path -> existingSongs.firstOrNull { song -> song.path == path } }
            val updates = if (songs.isEmpty()) emptyList() else listOf(MediaImporter.PlaylistUpdateData(type, PLAYLIST_NAME, songs, PLAYLIST_ID))
            emit(FlowEvent.Success(updates))
        }
    }

    private companion object {
        const val A = "/storage/emulated/0/Music/A/a.mp3"
        const val B = "/storage/emulated/0/Music/B/b.mp3"
        const val C = "/storage/emulated/0/Music/C/c.mp3"
        const val PLAYLIST_NAME = "stuff"
        const val PLAYLIST_ID = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2Fstuff.m3u"

        fun song(path: String) = Song(
            id = 0,
            name = path.substringAfterLast('/'),
            albumArtist = "Artist",
            artists = listOf("Artist"),
            album = "Album",
            track = 1,
            disc = 1,
            duration = 180_000,
            date = null,
            genres = emptyList(),
            path = path,
            size = 5_000,
            mimeType = "audio/mpeg",
            lastModified = Instant.fromEpochMilliseconds(1_700_000_000_000),
            lastPlayed = null,
            lastCompleted = null,
            playCount = 0,
            playbackPosition = 0,
            blacklisted = false,
            mediaProvider = MediaProviderType.Shuttle,
            lyrics = null,
            grouping = null,
            bitRate = null,
            bitDepth = null,
            sampleRate = null,
            channelCount = null
        )
    }
}
