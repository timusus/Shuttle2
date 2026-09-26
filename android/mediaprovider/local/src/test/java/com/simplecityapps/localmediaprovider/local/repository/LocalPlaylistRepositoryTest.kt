package com.simplecityapps.localmediaprovider.local.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.localmediaprovider.local.data.room.dao.toSong
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Playlist writes against a real (in-memory) database, whose foreign keys only accept songs in the library. */
@RunWith(AndroidJUnit4::class)
class LocalPlaylistRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java).allowMainThreadQueries().build()

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `songs not in the library are left out of playlist writes`() = runTest {
        val repository = LocalPlaylistRepository(context, backgroundScope, database.playlistDataDao(), database.playlistSongJoinDataDao(), database.songDataDao())
        val (first, second) = insertSongs("First", "Second")
        val openedFile = first.copy(id = -5, path = "content://downloads/1")

        val playlist = repository.createPlaylist("From the queue", MediaProviderType.Shuttle, listOf(openedFile, first, second), null)
        playlist.songCount shouldBe 2

        repository.addToPlaylist(playlist, listOf(openedFile, first))

        repository.getSongsForPlaylist(playlist).first().sortedBy { playlistSong -> playlistSong.sortOrder }.map { playlistSong -> playlistSong.song.name } shouldBe
            listOf("First", "Second", "First")
    }

    @Test
    fun `a playlist whose songs can't be added isn't created`() = runTest {
        val repository = LocalPlaylistRepository(context, backgroundScope, database.playlistDataDao(), database.playlistSongJoinDataDao(), database.songDataDao())
        val (song) = insertSongs("Song")

        shouldThrow<SQLiteConstraintException> {
            repository.createPlaylist("Broken", MediaProviderType.Shuttle, listOf(song, song.copy(id = song.id + 100)), null)
        }

        database.playlistDataDao().getAll().first() shouldBe emptyList()
    }

    @Test
    fun `concurrent first calls to getFavoritesPlaylist create only one playlist`() = runTest {
        val repository = LocalPlaylistRepository(context, backgroundScope, database.playlistDataDao(), database.playlistSongJoinDataDao(), database.songDataDao())

        val results = (1..10).map { async(Dispatchers.Default) { repository.getFavoritesPlaylist() } }.awaitAll()

        results.map { it.id }.distinct() shouldBe listOf(results.first().id)
        database.playlistDataDao().getAll().first().count { it.name == results.first().name } shouldBe 1
    }

    @Test
    fun `playlist cover songs are one per distinct album, in playlist order`() = runTest {
        val repository = LocalPlaylistRepository(context, backgroundScope, database.playlistDataDao(), database.playlistSongJoinDataDao(), database.songDataDao())
        val songs = insertSongsWithAlbums("First" to "Album A", "Second" to "Album A", "Third" to "Album B", "Fourth" to "Album C", "Fifth" to "Album D")
        val playlist = repository.createPlaylist("Mixed", MediaProviderType.Shuttle, songs, null)

        repository.getPlaylistCoverSongs(playlist, limit = 3).first().map { it.name } shouldBe listOf("First", "Third", "Fourth")
    }

    @Test
    fun `playlist cover songs match on album regardless of case`() = runTest {
        val repository = LocalPlaylistRepository(context, backgroundScope, database.playlistDataDao(), database.playlistSongJoinDataDao(), database.songDataDao())
        val songs = insertSongsWithAlbums("First" to "album", "Second" to "ALBUM")
        val playlist = repository.createPlaylist("Same album", MediaProviderType.Shuttle, songs, null)

        repository.getPlaylistCoverSongs(playlist, limit = 4).first().map { it.name } shouldBe listOf("First")
    }

    @Test
    fun `playlist cover songs follow the playlist's own sort order, not raw insertion order`() = runTest {
        val repository = LocalPlaylistRepository(context, backgroundScope, database.playlistDataDao(), database.playlistSongJoinDataDao(), database.songDataDao())
        val songs = insertSongsWithAlbums("Zebra" to "Album A", "Mango" to "Album B", "Apple" to "Album C")
        val playlist = repository.createPlaylist("By name", MediaProviderType.Shuttle, songs, null)
            .copy(sortOrder = PlaylistSongSortOrder.SongName)

        repository.getPlaylistCoverSongs(playlist, limit = 3).first().map { it.name } shouldBe listOf("Apple", "Mango", "Zebra")
    }

    @Test
    fun `playlist cover songs reverse when the playlist is sorted descending`() = runTest {
        val repository = LocalPlaylistRepository(context, backgroundScope, database.playlistDataDao(), database.playlistSongJoinDataDao(), database.songDataDao())
        val songs = insertSongsWithAlbums("First" to "Album A", "Second" to "Album A", "Third" to "Album B", "Fourth" to "Album C", "Fifth" to "Album D")
        val playlist = repository.createPlaylist("Mixed descending", MediaProviderType.Shuttle, songs, null)
            .copy(sortDescending = true)

        repository.getPlaylistCoverSongs(playlist, limit = 3).first().map { it.name } shouldBe listOf("Fifth", "Fourth", "Third")
    }

    @Test
    fun `playlist cover songs return fewer than the limit when the playlist has fewer distinct albums`() = runTest {
        val repository = LocalPlaylistRepository(context, backgroundScope, database.playlistDataDao(), database.playlistSongJoinDataDao(), database.songDataDao())
        val (song) = insertSongs("Only")
        val playlist = repository.createPlaylist("Small", MediaProviderType.Shuttle, listOf(song), null)

        repository.getPlaylistCoverSongs(playlist, limit = 4).first().map { it.name } shouldBe listOf("Only")
    }

    private suspend fun insertSongs(vararg names: String): List<Song> {
        database.songDataDao().insert(names.map { name -> songData(name) })
        return database.songDataDao().get().map { songData -> songData.toSong() }
    }

    private suspend fun insertSongsWithAlbums(vararg nameToAlbum: Pair<String, String>): List<Song> {
        database.songDataDao().insert(nameToAlbum.map { (name, album) -> songData(name, album) })
        val byName = database.songDataDao().get().associateBy { it.name }
        return nameToAlbum.map { (name, _) -> byName.getValue(name).toSong() }
    }

    private fun songData(name: String, album: String = "Album") = SongData(
        name = name,
        track = 1,
        disc = 1,
        duration = 180_000,
        year = null,
        genres = emptyList(),
        path = "/music/$name.mp3",
        albumArtist = "Artist",
        artists = listOf("Artist"),
        album = album,
        size = 0,
        mimeType = "audio/mpeg",
        lastModified = Date(0),
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )
}
