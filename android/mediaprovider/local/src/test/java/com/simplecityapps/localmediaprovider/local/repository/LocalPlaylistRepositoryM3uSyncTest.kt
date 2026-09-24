package com.simplecityapps.localmediaprovider.local.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistData
import com.simplecityapps.localmediaprovider.local.data.room.entity.toSongData
import com.simplecityapps.mediaprovider.M3uParser
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import io.kotest.matchers.shouldBe
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalPlaylistRepositoryM3uSyncTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val repository = LocalPlaylistRepository(
        context = context,
        scope = CoroutineScope(Dispatchers.Unconfined),
        playlistDataDao = database.playlistDataDao(),
        playlistSongJoinDao = database.playlistSongJoinDataDao()
    )

    @After
    fun tearDown() {
        database.close()
    }

    private fun createSong(path: String) = Song(
        id = 1,
        name = "Test Song",
        albumArtist = "Test Artist",
        artists = listOf("Test Artist"),
        album = null,
        track = null,
        disc = null,
        duration = 180_000,
        date = null,
        genres = emptyList(),
        path = path,
        size = 0,
        mimeType = "audio/mpeg",
        lastModified = null,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        externalId = null,
        mediaProvider = MediaProviderType.Shuttle,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )

    private suspend fun insertSong(song: Song): Long = database.songDataDao().insert(listOf(song.toSongData(MediaProviderType.Shuttle))).first()

    private suspend fun createM3uPlaylist(externalId: String?): Playlist {
        val id = database.playlistDataDao().insert(
            PlaylistData(
                name = "Imported",
                sortOrder = PlaylistSongSortOrder.Position,
                mediaProviderType = MediaProviderType.Shuttle,
                externalId = externalId
            )
        )
        return database.playlistDataDao().getPlaylist(id)
    }

    @Test
    fun `addToPlaylist rewrites the m3u file for a playlist imported from m3u`() = runTest {
        val file = File.createTempFile("playlist", ".m3u").apply { deleteOnExit() }
        val song = createSong(path = "/music/song1.mp3")
        val songId = insertSong(song)
        val playlist = createM3uPlaylist(externalId = Uri.fromFile(file).toString())

        repository.addToPlaylist(playlist, listOf(song.copy(id = songId)))

        val content = file.readText()
        content.startsWith("#EXTM3U") shouldBe true
        val parsed = M3uParser().parse(path = file.path, fileName = file.name, inputStream = file.inputStream())
        parsed.entries.map { it.location } shouldBe listOf(song.path)
    }

    @Test
    fun `removeFromPlaylist rewrites the m3u file to drop the removed song`() = runTest {
        val file = File.createTempFile("playlist", ".m3u").apply { deleteOnExit() }
        val song = createSong(path = "/music/song1.mp3")
        val songId = insertSong(song)
        val playlist = createM3uPlaylist(externalId = Uri.fromFile(file).toString())
        repository.addToPlaylist(playlist, listOf(song.copy(id = songId)))
        val playlistSong = repository.getSongsForPlaylist(playlist).first()

        repository.removeFromPlaylist(playlist, playlistSong)

        val parsed = M3uParser().parse(path = file.path, fileName = file.name, inputStream = file.inputStream())
        parsed.entries.isEmpty() shouldBe true
    }

    @Test
    fun `addToPlaylist does not touch the file for a playlist that was not imported from m3u`() = runTest {
        val song = createSong(path = "/music/song1.mp3")
        val songId = insertSong(song)
        val playlist = createM3uPlaylist(externalId = null)

        // Should not throw despite there being no file to write to.
        repository.addToPlaylist(playlist, listOf(song.copy(id = songId)))
    }

    @Test
    fun `isM3uSynced is true only for local playlists with an external id`() {
        val base = Playlist(
            id = 1,
            name = "Test",
            songCount = 0,
            duration = 0,
            sortOrder = PlaylistSongSortOrder.Position,
            mediaProvider = MediaProviderType.Shuttle,
            externalId = null
        )

        base.isM3uSynced() shouldBe false
        base.copy(externalId = "content://tree/doc").isM3uSynced() shouldBe true
        base.copy(externalId = "content://tree/doc", mediaProvider = MediaProviderType.Jellyfin).isM3uSynced() shouldBe false
    }
}
