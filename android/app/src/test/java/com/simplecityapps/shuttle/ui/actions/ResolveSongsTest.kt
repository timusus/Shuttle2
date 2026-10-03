package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createGenre
import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.toQueueItem
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ResolveSongsTest {

    private val songRepository = FakeSongRepository()
    private val genreRepository = FakeGenreRepository()
    private val playlistRepository = FakePlaylistRepository()
    private val queueOperations = FakeQueueOperations()
    private val resolveSongs = TestMediaActions(songRepository, genreRepository, playlistRepository, queueOperations).resolveSongs

    @Test
    fun `songs resolve to themselves, in their order`() = runTest {
        val songs = listOf(createSong(id = 2), createSong(id = 1))

        resolveSongs(MediaSelection.Songs(songs)) shouldBe songs
    }

    @Test
    fun `albums resolve to their songs in the default order`() = runTest {
        val track2 = createSong(id = 1, track = 2)
        val track1 = createSong(id = 2, track = 1)
        songRepository.setSongs(listOf(track2, track1))

        resolveSongs(MediaSelection.Albums(createAlbum())) shouldBe listOf(track1, track2)
    }

    @Test
    fun `artists resolve artist by artist in the selection's order, each in the default song order`() = runTest {
        songRepository.applyQueryPredicates = true
        val aTrack2 = createSong(id = 1, albumArtist = "A", track = 2)
        val aTrack1 = createSong(id = 2, albumArtist = "A", track = 1)
        val b = createSong(id = 3, albumArtist = "B")
        songRepository.setSongs(listOf(aTrack2, b, aTrack1))
        val artistA = createAlbumArtist(name = "A", groupKey = aTrack1.albumArtistGroupKey)
        val artistB = createAlbumArtist(name = "B", groupKey = b.albumArtistGroupKey)

        resolveSongs(MediaSelection.AlbumArtists(listOf(artistB, artistA))) shouldBe listOf(b, aTrack1, aTrack2)
        resolveSongs(MediaSelection.AlbumArtists(listOf(artistA, artistB))) shouldBe listOf(aTrack1, aTrack2, b)
    }

    @Test
    fun `a song credited to several selected artists sorts with the earliest of them`() = runTest {
        songRepository.applyQueryPredicates = true
        val collab = createSong(id = 1, albumArtist = "Various", artists = listOf("A", "B"))
        val onlyA = createSong(id = 2, albumArtist = "A", track = 2)
        val onlyB = createSong(id = 3, albumArtist = "B")
        songRepository.setSongs(listOf(onlyB, collab, onlyA))
        val keyA = onlyA.albumArtistGroupKey
        val keyB = onlyB.albumArtistGroupKey

        val resolved = resolveSongs(MediaSelection.AlbumArtists(listOf(createAlbumArtist(name = "B", groupKey = keyB), createAlbumArtist(name = "A", groupKey = keyA))))

        resolved.last() shouldBe onlyA
        resolved.take(2).toSet() shouldBe setOf(collab, onlyB)
    }

    @Test
    fun `artists resolve a large library quickly`() = runTest {
        songRepository.applyQueryPredicates = true
        val artistCount = 1_500
        val songs = List(30_000) { i -> createSong(id = i.toLong(), albumArtist = "artist-${i % artistCount}", artists = listOf("artist-${i % artistCount}", "artist-${(i + 1) % artistCount}"), track = i) }
        songRepository.setSongs(songs)
        val artists = (0 until artistCount).map { createAlbumArtist(name = "artist-$it", groupKey = songs[it].albumArtistGroupKey) }

        val start = TimeSource.Monotonic.markNow()
        val resolved = resolveSongs(MediaSelection.AlbumArtists(artists))

        resolved.size shouldBe songs.size
        // Generous: the old per-comparison search took minutes here; this only guards against it coming back.
        (start.elapsedNow() < 10.seconds) shouldBe true
    }

    @Test
    fun `genres resolve through the genre repository`() = runTest {
        val song = createSong(id = 1)
        genreRepository.setSongsForGenre("Rock", listOf(song))

        resolveSongs(MediaSelection.Genres(createGenre(name = "Rock"))) shouldBe listOf(song)
    }

    @Test
    fun `playlists resolve to their songs in playlist order`() = runTest {
        val playlist = createPlaylist(id = 1)
        val songs = listOf(createSong(id = 3), createSong(id = 1))
        playlistRepository.setSongsForPlaylist(playlist, songs)

        resolveSongs(MediaSelection.Playlists(playlist)) shouldBe songs
    }

    @Test
    fun `the queue resolves to its songs`() = runTest {
        val items = listOf(createSong(id = 1), createSong(id = 2)).map { it.toQueueItem(isCurrent = false) }
        queueOperations.queueStateFlow.value = QueueState(items = items, currentItem = null, currentPosition = null)

        resolveSongs(MediaSelection.Queue) shouldBe items.map { it.song }
    }
}
