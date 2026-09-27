package com.simplecityapps.localmediaprovider.local.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.localmediaprovider.local.data.room.dao.toSong
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.query.SongQuery
import io.kotest.matchers.shouldBe
import java.io.File
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
            playlistStore = playlistRepository,
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

    @Test
    fun `a rescan gives an m3u playlist the songs the file now lists, in its order`() = runBlocking<Unit> {
        provider.songPaths = listOf(A, B, C, D)
        provider.playlist = listOf(A, B, C)
        importer.import()

        provider.playlist = listOf(A, D, C)
        importer.import()

        importedPlaylistPaths() shouldBe listOf(A, D, C)
    }

    @Test
    fun `importing an m3u playlist leaves the file as it is`() = runBlocking<Unit> {
        val file = File.createTempFile("stuff", ".m3u").apply { deleteOnExit() }
        val contents = "#EXTM3U\n#EXTINF:180,Artist - Remix\nhttp://example.com/remix.mp3\nA/a.mp3\nD/d.mp3\nC/c.mp3\n"
        file.writeText(contents)
        provider.playlistId = Uri.fromFile(file).toString()
        provider.songPaths = listOf(A, B, C, D)
        provider.playlist = listOf(A, B, C)
        importer.import()

        provider.playlist = listOf(A, D, C)
        importer.import()

        importedPlaylistPaths() shouldBe listOf(A, D, C)
        file.readText() shouldBe contents
    }

    @Test
    fun `an m3u playlist named like one made in S2 is imported as a playlist of its own`() = runBlocking<Unit> {
        provider.songPaths = listOf(A, B, C)
        provider.playlist = listOf(A, C)
        importer.import()
        val songB = database.songDataDao().get().single { song -> song.path == B }.toSong()
        val made = playlistRepository.createPlaylist(PLAYLIST_NAME, MediaProviderType.Shuttle, listOf(songB), externalId = null)
        playlistRepository.deletePlaylist(database.playlistDataDao().getAll().first().single { playlist -> playlist.externalId == PLAYLIST_ID })

        importer.import()

        playlistPaths(made.id) shouldBe listOf(B)
        importedPlaylistPaths() shouldBe listOf(A, C)
    }

    @Test
    fun `a rescan keeps the songs added in S2 to a playlist from a media server`() = runBlocking<Unit> {
        val provider = FakeProvider(MediaProviderType.Jellyfin)
        importer.mediaProviders.clear()
        importer.mediaProviders += provider
        provider.songPaths = listOf(A, B, C)
        provider.playlist = listOf(A, B)
        importer.import()
        val imported = database.playlistDataDao().getAll().first().single { playlist -> playlist.externalId == PLAYLIST_ID }
        playlistRepository.addToPlaylist(imported, database.songDataDao().get().filter { song -> song.path == C }.map { song -> song.toSong() })

        importer.import()

        importedPlaylistPaths() shouldBe listOf(A, B, C)
    }

    /** The paths of the songs in the playlist imported from the provider's m3u, in order, read straight from the database. */
    private suspend fun importedPlaylistPaths(): List<String>? {
        val playlists = database.playlistDataDao().getAll().first().filter { playlist -> playlist.externalId != null }
        return playlistPaths(playlists.singleOrNull()?.id ?: return null)
    }

    private suspend fun playlistPaths(playlistId: Long): List<String> = database.playlistSongJoinDataDao().getSongsForPlaylist(playlistId).first().map { playlistSong -> playlistSong.song.path }

    /**
     * The songs [delegate] stores, except that [getSongs] still serves the empty library from before the import. The local
     * repository shares one song list across its collectors, and requeries it only after a write has returned, which takes a
     * while for a large library, so a read straight after the import can see the library as it was.
     */
    private class LaggingSongRepository(private val delegate: SongRepository) : SongRepository by delegate {
        override fun getSongs(query: SongQuery): Flow<List<Song>?> = flowOf(emptyList())
    }

    /** A provider that finds a song at each of [songPaths] and one playlist, [playlistId], listing [playlist]. */
    private class FakeProvider(override val type: MediaProviderType = MediaProviderType.Shuttle) : MediaProvider {
        @Volatile var songPaths: List<String> = emptyList()

        @Volatile var playlist: List<String> = emptyList()

        @Volatile var playlistId: String = PLAYLIST_ID

        override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = flowOf(FlowEvent.Success(songPaths.map { path -> song(path, type) }))

        override fun findPlaylists(existingSongs: List<Song>): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>> = flow {
            val songs = playlist.mapNotNull { path -> existingSongs.firstOrNull { song -> song.path == path } }
            val updates = if (songs.isEmpty()) emptyList() else listOf(MediaImporter.PlaylistUpdateData(type, PLAYLIST_NAME, songs, playlistId))
            emit(FlowEvent.Success(updates))
        }
    }

    private companion object {
        const val A = "/storage/emulated/0/Music/A/a.mp3"
        const val B = "/storage/emulated/0/Music/B/b.mp3"
        const val C = "/storage/emulated/0/Music/C/c.mp3"
        const val D = "/storage/emulated/0/Music/D/d.mp3"
        const val PLAYLIST_NAME = "stuff"
        const val PLAYLIST_ID = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2Fstuff.m3u"

        fun song(
            path: String,
            mediaProviderType: MediaProviderType
        ) = Song(
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
            mediaProvider = mediaProviderType,
            lyrics = null,
            grouping = null,
            bitRate = null,
            bitDepth = null,
            sampleRate = null,
            channelCount = null
        )
    }
}
