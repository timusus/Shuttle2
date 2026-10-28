package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.database.InMemoryDatabaseTest
import com.simplecityapps.localmediaprovider.local.data.room.database.inMemoryMediaDatabaseBuilder
import com.simplecityapps.localmediaprovider.local.data.room.database.trackingIdentityChanges
import com.simplecityapps.mediaprovider.repository.genres.GenreQuery
import com.simplecityapps.shuttle.model.MinTrackLength
import com.simplecityapps.shuttle.query.SongQuery
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/** A genre's cover songs, straight from a real (in-memory) database (#633). */
class LocalGenreRepositoryTest : InMemoryDatabaseTest() {
    private val database = inMemoryMediaDatabaseBuilder().trackingIdentityChanges().build()

    @AfterTest
    fun tearDown() {
        database.close()
    }

    private suspend fun insert(vararg songs: Triple<String, String, List<String>>) {
        database.songDataDao().insert(
            songs.mapIndexed { index, (album, albumArtist, genres) -> createSongData(album, albumArtist, track = index + 1).copy(genres = genres) },
        )
    }

    private val minimum = MutableStateFlow(MinTrackLength.Off)

    private fun TestScope.genreRepository(): LocalGenreRepository = database.libraryAlbumIndex().let { index ->
        val songs = LocalSongRepository(backgroundScope, UnconfinedTestDispatcher(testScheduler), database.songDataDao(), index, minimum)
        LocalGenreRepository(backgroundScope, UnconfinedTestDispatcher(testScheduler), database.songDataDao(), songs, index, minimum)
    }

    @Test
    fun `genres total each song once per genre and leave out excluded and too short songs`() = runTest {
        database.songDataDao().insert(
            listOf(
                createSongData("A", track = 1).copy(genres = listOf("Jazz", "Spiritual", "Jazz")),
                createSongData("B", track = 2).copy(genres = listOf("Jazz")),
                createSongData("C", track = 3).copy(genres = listOf("Jazz"), duration = 5_000),
                createSongData("D", track = 4).copy(genres = listOf("Jazz")),
                createSongData("E", track = 5).copy(genres = emptyList()),
            ),
        )
        val repository = genreRepository()
        database.songDataDao().setExcluded(listOf(database.songDataDao().get().first { it.album == "D" }.id), true)

        repository.getGenres(GenreQuery.All()).first().map { Triple(it.name, it.songCount, it.duration) }.sortedBy { it.first } shouldBe
            listOf(Triple("Jazz", 3, 365_000), Triple("Spiritual", 1, 180_000))

        minimum.value = MinTrackLength.TenSeconds
        // The relay re-reads on a change, so wait for the new total rather than the cached one
        repository.getGenres(GenreQuery.All()).first { genres -> genres.first { it.name == "Jazz" }.songCount == 2 }.map { it.name }.sorted() shouldBe listOf("Jazz", "Spiritual")
    }

    @Test
    fun `songs for genres list a song once per genre it is in`() = runTest {
        insert(
            Triple("A", "X", listOf("Jazz", "Spiritual")),
            Triple("B", "X", listOf("Jazz")),
            Triple("C", "X", listOf("Rock")),
        )
        val repository = genreRepository()

        repository.getSongsForGenres(listOf("Jazz", "Spiritual"), SongQuery.All()).first().mapNotNull { it.album }.sorted() shouldBe listOf("A", "A", "B")
    }

    @Test
    fun `genre cover songs are one per album of the genre by artist then album up to the limit`() = runTest {
        insert(
            Triple("Kind of Blue", "Miles Davis", listOf("Jazz")),
            Triple("kind of blue", "miles davis", listOf("Jazz")),
            Triple("A Love Supreme", "John Coltrane", listOf("Jazz", "Spiritual")),
            Triple("Blue Train", "John Coltrane", listOf("Jazz")),
            Triple("Mingus Ah Um", "Charles Mingus", listOf("Jazz")),
            Triple("Time Out", "Dave Brubeck", listOf("Jazz")),
            Triple("OK Computer", "Radiohead", listOf("Rock")),
            Triple("Jazz Odyssey", "Spinal Tap", listOf("Jazz Fusion")),
        )
        val repository = genreRepository()

        repository.getGenreCoverSongs("Jazz", limit = 4).first().map { it.album } shouldBe
            listOf("Mingus Ah Um", "Time Out", "A Love Supreme", "Blue Train")
        repository.getGenreCoverSongs("Jazz", limit = 10).first().map { it.album }.size shouldBe 5
    }

    @Test
    fun `genre cover songs are one per album identity instead of per album tag`() = runTest {
        // One release, tagged with two spellings of its album artist: one album by its MusicBrainz release id
        database.songDataDao().insert(
            listOf(
                createSongData("Bitches Brew", "Miles Davis", track = 1).copy(genres = listOf("Jazz"), mbAlbumId = "release-1"),
                createSongData("Bitches Brew", "Miles Davis Quintet", track = 2).copy(genres = listOf("Jazz"), mbAlbumId = "release-1"),
                createSongData("Head Hunters", "Herbie Hancock", track = 1).copy(genres = listOf("Jazz")),
            ),
        )
        val repository = genreRepository()

        repository.getGenreCoverSongs("Jazz", limit = 4).first().map { it.album } shouldBe listOf("Head Hunters", "Bitches Brew")
    }

    @Test
    fun `genre cover songs prefer albums with artwork and a song of an album that has it`() = runTest {
        database.songDataDao().insert(
            listOf(
                createSongData("Bare Album", "Artist A", track = 1).copy(genres = listOf("Dance")),
                createSongData("Covered Album", "Artist B", track = 1).copy(genres = listOf("Dance")),
                createSongData("Covered Album", "Artist B", track = 2).copy(genres = listOf("Dance"), artworkVersion = "v1"),
                createSongData("Other Covered", "Artist C", track = 1).copy(genres = listOf("Dance"), artworkVersion = "v2"),
            ),
        )
        val repository = genreRepository()

        val covers = repository.getGenreCoverSongs("Dance", limit = 2).first()

        covers.map { it.album } shouldBe listOf("Covered Album", "Other Covered")
        covers.map { it.artworkVersion } shouldBe listOf("v1", "v2")
        repository.getGenreCoverSongs("Dance", limit = 4).first().map { it.album } shouldBe listOf("Covered Album", "Other Covered", "Bare Album")
    }

    @Test
    fun `excluded songs and other genres are left out`() = runTest {
        insert(Triple("Kid A", "Radiohead", listOf("Electronic")), Triple("Amnesiac", "Radiohead", listOf("Electronic")))
        val kidA = database.songDataDao().get().first { it.album == "Kid A" }
        database.songDataDao().setExcluded(listOf(kidA.id), true)
        val repository = genreRepository()

        repository.getGenreCoverSongs("Electronic", limit = 4).first().map { it.album } shouldBe listOf("Amnesiac")
        repository.getGenreCoverSongs("Electro", limit = 4).first() shouldBe emptyList()
    }
}
