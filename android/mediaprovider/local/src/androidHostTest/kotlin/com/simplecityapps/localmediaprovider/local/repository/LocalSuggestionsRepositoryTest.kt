package com.simplecityapps.localmediaprovider.local.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.suggestions.ImportDays
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalSuggestionsRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val songDao = database.songDataDao()
    private val repository = LocalSuggestionsRepository(database.suggestionsDao())

    private val now = Instant.parse("2026-09-23T08:00:00Z")

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insert(vararg songs: SongData) {
        songDao.insert(songs.toList())
    }

    private fun key(
        album: String,
        artist: String = "artist"
    ) = AlbumGroupKey(album, AlbumArtistGroupKey(artist))

    @Test
    fun `albums are looked up by group key as the album repository builds them, in the keys' order`() = runTest {
        insert(
            createSongData(album = "The Bends", albumArtist = "Radiohead", track = 1),
            createSongData(album = "Bends", albumArtist = "The Radiohead", track = 2),
            createSongData(album = "Kid A", albumArtist = "Radiohead", track = 1),
            createSongData(album = "Blue", albumArtist = "Joni Mitchell", track = 1)
        )
        val repositoryAlbums = LocalAlbumRepository(backgroundScope, songDao).getAlbums(AlbumQuery.All()).first().associateBy { it.groupKey }
        val bends = key("bends", "radiohead")
        val kidA = key("kid a", "radiohead")

        repository.albums(listOf(kidA, key("missing"), bends)) shouldBe listOf(repositoryAlbums[kidA], repositoryAlbums[bends])
        repository.albums(listOf(bends)).single().songCount shouldBe 2
    }

    @Test
    fun `album artists are looked up by group key, with or without an album artist tag`() = runTest {
        insert(
            createSongData(album = "Blue", albumArtist = "Joni Mitchell", track = 1),
            createSongData(album = "Hejira", albumArtist = "Joni Mitchell", track = 1),
            createSongData(album = "Mezzanine", albumArtist = "x", track = 1).copy(albumArtist = null, artists = listOf("The Massive Attack"))
        )
        val repositoryArtists = LocalAlbumArtistRepository(backgroundScope, songDao).getAlbumArtists(AlbumArtistQuery.All()).first().associateBy { it.groupKey }
        val joni = AlbumArtistGroupKey("joni mitchell")
        val massive = AlbumArtistGroupKey("massive attack")

        repository.albumArtists(listOf(massive, joni)) shouldBe listOf(repositoryArtists[massive], repositoryArtists[joni])
        repository.albumArtists(listOf(joni)).single().albumCount shouldBe 2
    }

    @Test
    fun `genres sum each tagged song, and the largest need a minimum`() = runTest {
        insert(
            createSongData(album = "A", track = 1).copy(genres = listOf("Jazz", "Soul")),
            createSongData(album = "A", track = 2).copy(genres = listOf("Jazz")),
            createSongData(album = "B", track = 1).copy(genres = listOf("Soul")),
            createSongData(album = "B", track = 2).copy(genres = listOf("Soul"))
        )

        repository.genres(listOf("Soul", "Jazz", "Polka")).map { it.name to it.songCount } shouldBe listOf("Soul" to 3, "Jazz" to 2)
        repository.largestGenres(minSongs = 2, limit = 10).map { it.name } shouldBe listOf("Soul", "Jazz")
        repository.largestGenres(minSongs = 3, limit = 10).map { it.name } shouldBe listOf("Soul")
        repository.largestGenres(minSongs = 2, limit = 1).map { it.name } shouldBe listOf("Soul")
    }

    @Test
    fun `recently completed albums are ordered by their last play through`() = runTest {
        insert(
            createSongData(album = "Old", track = 1).copy(lastCompleted = now - 9.days),
            createSongData(album = "New", track = 1).copy(lastCompleted = now - 1.days),
            createSongData(album = "Old", track = 2).copy(lastCompleted = now - 5.days),
            createSongData(album = "Never", track = 1)
        )

        repository.recentlyCompletedAlbums(10) shouldBe listOf(key("new"), key("old"))
        repository.recentlyCompletedAlbums(1) shouldBe listOf(key("new"))
    }

    @Test
    fun `recently added albums are those with a song added since a time, newest first`() = runTest {
        insert(
            createSongData(album = "Old", track = 1).copy(dateAdded = now - 90.days),
            createSongData(album = "Mixed", track = 1).copy(dateAdded = now - 80.days),
            createSongData(album = "Mixed", track = 2).copy(dateAdded = now - 2.days),
            createSongData(album = "New", track = 1).copy(dateAdded = now - 10.days)
        )

        repository.recentlyAddedAlbums(since = now - 60.days, limit = 10) shouldBe listOf(key("mixed"), key("new"))
    }

    @Test
    fun `albums to rediscover were played enough or hold a favourite, and not lately`() = runTest {
        insert(
            createSongData(album = "Loved", track = 1, playCount = 2).copy(lastPlayed = now - 200.days),
            createSongData(album = "Loved", track = 2, playCount = 2).copy(lastPlayed = now - 120.days),
            createSongData(album = "Recent", track = 1, playCount = 9).copy(lastPlayed = now - 200.days),
            createSongData(album = "Recent", track = 2).copy(lastPlayed = now - 10.days),
            createSongData(album = "Barely", track = 1, playCount = 2).copy(lastPlayed = now - 200.days),
            createSongData(album = "Favourite", track = 1).copy(favouritedAt = now - 300.days)
        )

        repository.albumsToRediscover(minPlays = 3, playedBefore = now - 90.days, limit = 10) shouldBe listOf(key("loved"), key("favourite"))
    }

    @Test
    fun `import days count the library and its busiest day`() = runTest {
        repository.importDays() shouldBe ImportDays(songs = 0, largestDay = 0)
        insert(
            createSongData(album = "A", track = 1).copy(dateAdded = now),
            createSongData(album = "A", track = 2).copy(dateAdded = now + 1.hours),
            createSongData(album = "B", track = 1).copy(dateAdded = now - 3.days)
        )

        repository.importDays() shouldBe ImportDays(songs = 3, largestDay = 2)
    }

    @Test
    fun `excluded songs are left out`() = runTest {
        insert(createSongData(album = "Hidden", track = 1).copy(excluded = true, lastCompleted = now))

        repository.recentlyCompletedAlbums(10).shouldBeEmpty()
        repository.songCount().first() shouldBe 0
    }
}
