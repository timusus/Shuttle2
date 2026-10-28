package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.database.InMemoryDatabaseTest
import com.simplecityapps.localmediaprovider.local.data.room.database.inMemoryMediaDatabaseBuilder
import com.simplecityapps.localmediaprovider.local.data.room.database.trackingIdentityChanges
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/** A play, a playback position or a favourite reaches the song lists without the whole library being read again. */
class LibraryStatsTest : InMemoryDatabaseTest() {
    private val database = inMemoryMediaDatabaseBuilder().trackingIdentityChanges().build()
    private val dao = database.songDataDao()

    @AfterTest
    fun tearDown() {
        database.close()
    }

    private fun TestScope.songs() = LocalSongRepository(backgroundScope, UnconfinedTestDispatcher(testScheduler), dao, database.libraryAlbumIndex())

    /** Every read of the whole library, in order. */
    private fun TestScope.libraryReads(): Channel<List<Song>> = Channel<List<Song>>(Channel.UNLIMITED).also { reads ->
        backgroundScope.launch { dao.getLibrary().collect(reads::send) }
    }

    @Test
    fun `a play position and favourite reach the songs without reading the library again`() = runTest {
        dao.insert(listOf(createSongData(album = "Blue", track = 1), createSongData(album = "Blue", track = 2)))
        val reads = libraryReads()
        val (first, second) = reads.receive()
        val repository = songs()

        repository.recordPlayedThrough(first)
        repository.setPlaybackPosition(second, 30_000)
        repository.setFavourite(listOf(second), favourite = true)

        val played = repository.getSongs(SongQuery.All()).first { songs -> songs?.find { it.id == second.id }?.favouritedAt != null }!!
        played.map { it.playCount } shouldBe listOf(1, 0)
        // Played through leaves the position at the song's duration
        played.map { it.playbackPosition } shouldBe listOf(180_000, 30_000)
        // The next read of the whole library is the exclusion's, not one per play
        repository.setExcluded(listOf(first), excluded = true)
        reads.receive().map { it.blacklisted to it.playCount } shouldBe listOf(true to 1, false to 0)
    }

    @Test
    fun `an album's play count follows a play`() = runTest {
        dao.insert(listOf(createSongData(album = "Blue", track = 1), createSongData(album = "Blue", track = 2, playCount = 2)))
        val albums = LocalAlbumRepository(backgroundScope, UnconfinedTestDispatcher(testScheduler), dao.getAll())
        val first = songs().loadSongs(SongQuery.All()).first()

        songs().recordPlayedThrough(first)

        albums.getAlbums(AlbumQuery.All()).first { it.singleOrNull()?.playCount == 3 }.single().playCount shouldBe 3
    }

    @Test
    fun `a sync of unchanged songs whose server play count rose leaves the library as read`() = runTest {
        dao.insert(listOf(createSongData(album = "Blue", track = 1).copy(mediaProvider = MediaProviderType.Jellyfin, externalId = "j1", dateAdded = Instant.fromEpochSeconds(1_700_000_000))))
        val reads = libraryReads()
        val stored = reads.receive().single()
        val repository = songs()

        repository.insertUpdateAndDelete(emptyList(), listOf(stored.copy(playCount = 4)), emptyList(), MediaProviderType.Jellyfin)

        repository.getSongs(SongQuery.All()).first { songs -> songs?.single()?.playCount == 4 }!!.single().playCount shouldBe 4
        repository.setExcluded(listOf(stored), excluded = true)
        reads.receive().single().let { it.blacklisted to it.playCount } shouldBe (true to 4)
    }
}
