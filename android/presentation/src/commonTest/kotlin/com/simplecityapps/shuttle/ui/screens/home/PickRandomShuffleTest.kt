package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.random.Random
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class PickRandomShuffleTest {
    private val songRepository = FakeSongRepository().apply { applyQueryPredicates = true }

    private fun pick(by: ShuffleBy, seed: Int = 0) = PickRandomShuffle(songRepository, Random(seed))

    private fun withGenres(song: Song, vararg genres: String) = song.copy(genres = genres.toList())

    private fun songsOf(shuffle: RandomShuffle?): List<Long> = shuffle!!.action.shouldBeInstanceOf<MediaAction.Shuffle>().selection.shouldBeInstanceOf<MediaSelection.Songs>().songs.map { it.id }

    @Test
    fun `an empty library picks nothing for every kind`() = runTest {
        songRepository.setSongs(emptyList())

        ShuffleBy.entries.forEach { by -> pick(by)(by) shouldBe null }
    }

    @Test
    fun `an artist is picked with all its songs and no one else's`() = runTest {
        songRepository.setSongs(
            listOf(
                createSong(id = 1, albumArtist = "Band"),
                createSong(id = 2, albumArtist = "Band"),
                createSong(id = 3, albumArtist = "Other"),
            ),
        )

        val seen = (0 until 20).map { seed -> pick(ShuffleBy.Artist, seed)(ShuffleBy.Artist)!! }.associate { it.label to it.action }

        seen shouldBe mapOf(
            "Band" to MediaAction.Shuffle(MediaSelection.SongsMatching(SongQuery.ArtistGroupKey(createSong(id = 1, albumArtist = "Band").albumArtistGroupKey))),
            "Other" to MediaAction.Shuffle(MediaSelection.SongsMatching(SongQuery.ArtistGroupKey(createSong(id = 3, albumArtist = "Other").albumArtistGroupKey))),
        )
    }

    @Test
    fun `an artist pick holds no songs until it resolves`() = runTest {
        songRepository.setSongs(listOf(createSong(id = 1, albumArtist = "Band"), createSong(id = 2, albumArtist = "Band"), createSong(id = 3, albumArtist = "Other")))

        val selection = pick(ShuffleBy.Artist)(ShuffleBy.Artist)!!.action.shouldBeInstanceOf<MediaAction.Shuffle>().selection.shouldBeInstanceOf<MediaSelection.SongsMatching>()

        songRepository.loadSongs(selection.query).map { it.albumArtist }.distinct().size shouldBe 1
    }

    @Test
    fun `songs with no album artist are never an artist pick`() = runTest {
        songRepository.setSongs(listOf(createSong(id = 1, albumArtist = ""), createSong(id = 2, albumArtist = "  ")))

        pick(ShuffleBy.Artist)(ShuffleBy.Artist) shouldBe null
    }

    @Test
    fun `a song with several genres belongs to each and songs with none are left out`() = runTest {
        songRepository.setSongs(
            listOf(
                withGenres(createSong(id = 1), "Rock", "Jazz"),
                withGenres(createSong(id = 2), "Rock"),
                createSong(id = 3),
            ),
        )

        val seen = (0 until 20).map { seed -> pick(ShuffleBy.Genre, seed)(ShuffleBy.Genre)!! }.associate { it.label to songsOf(it) }

        seen shouldBe mapOf("Rock" to listOf(1L, 2L), "Jazz" to listOf(1L))
    }

    @Test
    fun `a year is picked from songs that have one and undated songs are left out`() = runTest {
        songRepository.setSongs(
            listOf(
                createSong(id = 1, date = LocalDate(1999, 5, 1)),
                createSong(id = 2, date = LocalDate(1999, 12, 31)),
                createSong(id = 3, date = LocalDate(2004, 1, 1)),
                createSong(id = 4).copy(date = null),
            ),
        )

        val seen = (0 until 20).map { seed -> pick(ShuffleBy.Year, seed)(ShuffleBy.Year)!! }.associate { it.label to songsOf(it) }

        seen shouldBe mapOf("1999" to listOf(1L, 2L), "2004" to listOf(3L))
    }
}
