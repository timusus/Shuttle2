package com.simplecityapps.localmediaprovider.local.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.database.trackingIdentityChanges
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** A genre's cover songs, straight from a real (in-memory) database (#633). */
@RunWith(AndroidJUnit4::class)
class LocalGenreRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java).trackingIdentityChanges().allowMainThreadQueries().build()

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insert(vararg songs: Triple<String, String, List<String>>) {
        database.songDataDao().insert(
            songs.mapIndexed { index, (album, albumArtist, genres) -> createSongData(album, albumArtist, track = index + 1).copy(genres = genres) },
        )
    }

    @Test
    fun `genre cover songs are one per album of the genre, by artist then album, up to the limit`() = runTest {
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
        val repository = database.libraryAlbumIndex().let { index -> LocalGenreRepository(backgroundScope, LocalSongRepository(backgroundScope, database.songDataDao(), index), database.songDataDao(), index) }

        repository.getGenreCoverSongs("Jazz", limit = 4).first().map { it.album } shouldBe
            listOf("Mingus Ah Um", "Time Out", "A Love Supreme", "Blue Train")
        repository.getGenreCoverSongs("Jazz", limit = 10).first().map { it.album }.size shouldBe 5
    }

    @Test
    fun `genre cover songs are one per album identity, not per album tag`() = runTest {
        // One release, tagged with two spellings of its album artist: one album by its MusicBrainz release id
        database.songDataDao().insert(
            listOf(
                createSongData("Bitches Brew", "Miles Davis", track = 1).copy(genres = listOf("Jazz"), mbAlbumId = "release-1"),
                createSongData("Bitches Brew", "Miles Davis Quintet", track = 2).copy(genres = listOf("Jazz"), mbAlbumId = "release-1"),
                createSongData("Head Hunters", "Herbie Hancock", track = 1).copy(genres = listOf("Jazz")),
            ),
        )
        val repository = database.libraryAlbumIndex().let { index -> LocalGenreRepository(backgroundScope, LocalSongRepository(backgroundScope, database.songDataDao(), index), database.songDataDao(), index) }

        repository.getGenreCoverSongs("Jazz", limit = 4).first().map { it.album } shouldBe listOf("Head Hunters", "Bitches Brew")
    }

    @Test
    fun `excluded songs and other genres are left out`() = runTest {
        insert(Triple("Kid A", "Radiohead", listOf("Electronic")), Triple("Amnesiac", "Radiohead", listOf("Electronic")))
        val kidA = database.songDataDao().get().first { it.album == "Kid A" }
        database.songDataDao().setExcluded(listOf(kidA.id), true)
        val repository = database.libraryAlbumIndex().let { index -> LocalGenreRepository(backgroundScope, LocalSongRepository(backgroundScope, database.songDataDao(), index), database.songDataDao(), index) }

        repository.getGenreCoverSongs("Electronic", limit = 4).first().map { it.album } shouldBe listOf("Amnesiac")
        repository.getGenreCoverSongs("Electro", limit = 4).first() shouldBe emptyList()
    }
}
