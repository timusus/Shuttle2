package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createAlbum
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.mediaprovider.SimilarSongsProvider
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class StartRadioTest {

    private val songRepository = FakeSongRepository().apply { applyQueryPredicates = true }
    private val queue = FakeQueueOperations()
    private val genreRepository = FakeGenreRepository()
    private val actions = TestMediaActions(songRepository = songRepository, genreRepository = genreRepository, queueOperations = queue)
    private val startRadio = actions.startRadio

    private fun song(id: Long, artist: String = "Other", genre: String? = null, provider: MediaProviderType = MediaProviderType.Shuttle, externalId: String? = null): Song = createSong(id = id, albumArtist = artist, mediaProvider = provider)
        .copy(genres = listOfNotNull(genre), externalId = externalId)

    private fun genre(name: String) = Genre(name = name, songCount = 1, duration = 0, mediaProviders = emptyList())

    private fun similar(provider: MediaProviderType, vararg ids: String) = object : SimilarSongsProvider {
        override fun handles(song: Song) = song.mediaProvider == provider
        override suspend fun similarSongIds(seed: Song, limit: Int) = ids.toList()
    }

    @Test
    fun `server mix keeps library songs in server order and drops the seed and unknown ids`() = runTest {
        val seed = song(1, provider = MediaProviderType.Jellyfin, externalId = "a")
        val b = song(2, provider = MediaProviderType.Jellyfin, externalId = "b")
        val c = song(3, provider = MediaProviderType.Jellyfin, externalId = "c")
        songRepository.setSongs(listOf(seed, c, b))
        actions.similarSongsProviders += similar(MediaProviderType.Jellyfin, "c", "a", "missing", "b")

        val result = startRadio(MediaSelection.Songs(listOf(seed)))

        result.shouldBeInstanceOf<StartRadio.Result.Success>()
        queue.lastSetQueue!!.map { it.id } shouldContainExactly listOf(1L, 3L, 2L)
    }

    @Test
    fun `local fallback uses the seed artist and genre and never the seed`() = runTest {
        val seed = song(1, artist = "Band", genre = "Jazz")
        val sameArtist = song(2, artist = "Band")
        val sameGenre = song(3, genre = "jazz")
        val unrelated = song(4, genre = "Metal")
        songRepository.setSongs(listOf(seed, sameArtist, sameGenre, unrelated))
        genreRepository.setGenres(listOf(genre("Jazz")))
        genreRepository.setSongsForGenre("Jazz", listOf(seed, sameGenre))

        startRadio(MediaSelection.Songs(listOf(seed)))

        val queued = queue.lastSetQueue!!
        queued.first() shouldBe seed
        queued.drop(1).map { it.id }.toSet() shouldBe setOf(2L, 3L)
        queued.filter { it.id == 1L }.size shouldBe 1
    }

    @Test
    fun `a seed tagged jazz draws songs tagged Jazz`() = runTest {
        val seed = song(1, artist = "A", genre = "jazz")
        val tagged = song(2, artist = "B", genre = "Jazz")
        val unrelated = song(3, artist = "C", genre = "Metal")
        songRepository.setSongs(listOf(seed, tagged, unrelated))
        genreRepository.setGenres(listOf(genre("Jazz"), genre("Metal")))
        genreRepository.setSongsForGenre("Jazz", listOf(tagged))
        genreRepository.setSongsForGenre("Metal", listOf(unrelated))

        startRadio(MediaSelection.Songs(listOf(seed)))

        queue.lastSetQueue!!.map { it.id } shouldContainExactly listOf(1L, 2L)
    }

    @Test
    fun `local fallback queries by artist and genre and never loads the whole library`() = runTest {
        val seed = song(1, artist = "Band", genre = "Jazz")
        songRepository.setSongs(listOf(seed, song(2, artist = "Band")))

        startRadio(MediaSelection.Songs(listOf(seed)))

        songRepository.queries.filterIsInstance<SongQuery.All>() shouldBe emptyList()
        songRepository.queries.filterIsInstance<SongQuery.ArtistGroupKeys>().size shouldBe 1
    }

    @Test
    fun `server mix looks the ids up by external id and never loads the whole library`() = runTest {
        val seed = song(1, provider = MediaProviderType.Jellyfin, externalId = "a")
        songRepository.setSongs(listOf(seed, song(2, provider = MediaProviderType.Jellyfin, externalId = "b")))
        actions.similarSongsProviders += similar(MediaProviderType.Jellyfin, "b")

        startRadio(MediaSelection.Songs(listOf(seed)))

        songRepository.queries.filterIsInstance<SongQuery.All>() shouldBe emptyList()
        songRepository.queries.filterIsInstance<SongQuery.ExternalIds>() shouldBe listOf(SongQuery.ExternalIds(listOf("b"), MediaProviderType.Jellyfin))
    }

    @Test
    fun `local fallback is capped at 50 songs`() = runTest {
        val seed = song(1, artist = "Band")
        songRepository.setSongs(listOf(seed) + (2L..120L).map { song(it, artist = "Band") })

        startRadio(MediaSelection.Songs(listOf(seed)))

        queue.lastSetQueue!!.size shouldBe 51
    }

    @Test
    fun `falls back to the library when the server mix holds nothing in the library`() = runTest {
        val seed = song(1, artist = "Band", provider = MediaProviderType.Jellyfin, externalId = "a")
        val sameArtist = song(2, artist = "Band", provider = MediaProviderType.Jellyfin, externalId = "b")
        songRepository.setSongs(listOf(seed, sameArtist))
        actions.similarSongsProviders += similar(MediaProviderType.Jellyfin, "unknown")

        startRadio(MediaSelection.Songs(listOf(seed)))

        queue.lastSetQueue!!.map { it.id } shouldContainExactly listOf(1L, 2L)
    }

    @Test
    fun `an album seed does not prepend a song`() = runTest {
        val seedSong = createSong(id = 1, album = "Album", albumArtist = "Band")
        val other = createSong(id = 2, albumArtist = "Band", album = "Else")
        songRepository.setSongs(listOf(seedSong, other))

        startRadio(MediaSelection.Albums(listOf(createAlbum(name = "Album", albumArtist = "Band", groupKey = seedSong.albumGroupKey))))

        val queued = queue.lastSetQueue!!
        queued.map { it.id } shouldContainExactly listOf(2L)
        queued shouldNotContain seedSong
    }

    @Test
    fun `no similar songs gives NoSongs and leaves the queue alone`() = runTest {
        val seed = song(1, artist = "Band")
        songRepository.setSongs(listOf(seed))

        startRadio(MediaSelection.Songs(listOf(seed))) shouldBe StartRadio.Result.NoSongs
        queue.lastSetQueue shouldBe null
    }
}
