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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.util.Date
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
        val repository = LocalPlaylistRepository(context, backgroundScope, database.playlistDataDao(), database.playlistSongJoinDataDao())
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
        val repository = LocalPlaylistRepository(context, backgroundScope, database.playlistDataDao(), database.playlistSongJoinDataDao())
        val (song) = insertSongs("Song")

        shouldThrow<SQLiteConstraintException> {
            repository.createPlaylist("Broken", MediaProviderType.Shuttle, listOf(song, song.copy(id = song.id + 100)), null)
        }

        database.playlistDataDao().getAll().first() shouldBe emptyList()
    }

    private suspend fun insertSongs(vararg names: String): List<Song> {
        database.songDataDao().insert(names.map(::songData))
        return database.songDataDao().get().map { songData -> songData.toSong() }
    }

    private fun songData(name: String) = SongData(
        name = name,
        track = 1,
        disc = 1,
        duration = 180_000,
        year = null,
        genres = emptyList(),
        path = "/music/$name.mp3",
        albumArtist = "Artist",
        artists = listOf("Artist"),
        album = "Album",
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
