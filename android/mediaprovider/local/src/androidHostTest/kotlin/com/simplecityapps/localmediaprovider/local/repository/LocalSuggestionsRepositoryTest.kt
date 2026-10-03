package com.simplecityapps.localmediaprovider.local.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.database.trackingIdentityChanges
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.genres.GenreQuery
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.MinTrackLength
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalSuggestionsRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java)
        .trackingIdentityChanges()
        .allowMainThreadQueries()
        .build()
    private val songDao = database.songDataDao()
    private val minimum = MutableStateFlow(MinTrackLength.Off)

    /** As the app builds it: Home's songs, genres, albums and artists are the library repositories'. */
    private fun TestScope.repositories(): Pair<LocalSuggestionsRepository, LocalGenreRepository> {
        val albumIndex = freshAlbumIndex(database)
        val songs = LocalSongRepository(backgroundScope, songDao, database.libraryAlbumIndex(), minimum)
        val genres = LocalGenreRepository(backgroundScope, songs, database.libraryAlbumIndex())
        return LocalSuggestionsRepository(database.suggestionsDao(), albumIndex, songs, genres) to genres
    }

    private val TestScope.repository get() = repositories().first

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
    fun `genres sum each tagged song`() = runTest {
        insert(
            createSongData(album = "A", track = 1).copy(genres = listOf("Jazz", "Soul")),
            createSongData(album = "A", track = 2).copy(genres = listOf("Jazz")),
            createSongData(album = "B", track = 1).copy(genres = listOf("Soul")),
            createSongData(album = "B", track = 2).copy(genres = listOf("Soul"))
        )

        repository.genres().map { it.name to it.songCount }.sortedBy { it.first } shouldBe listOf("Jazz" to 2, "Soul" to 3)
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
    fun `recently added albums are the newest by their newest song, however long ago`() = runTest {
        insert(
            createSongData(album = "Old", track = 1).copy(dateAdded = now - 900.days),
            createSongData(album = "Mixed", track = 1).copy(dateAdded = now - 800.days),
            createSongData(album = "Mixed", track = 2).copy(dateAdded = now - 2.days),
            createSongData(album = "New", track = 1).copy(dateAdded = now - 10.days)
        )

        repository.recentlyAddedAlbums(limit = 10) shouldBe listOf(key("mixed"), key("new"), key("old"))
        repository.recentlyAddedAlbums(limit = 2) shouldBe listOf(key("mixed"), key("new"))
    }

    @Test
    fun `a Jellyfin library imported at once has its newest albums (#649)`() = runTest {
        // As the Jellyfin mapper stores a song: DateCreated, the server's own scan time, as both dateAdded and
        // lastModified. The test server scanned every song on the same day, fractions of a second apart.
        val scanned = Instant.parse("2026-09-28T10:29:35.0745155Z")
        val albums = (1..40).map { "Album $it" }
        insert(
            *albums.flatMapIndexed { index, album ->
                (1..3).map { track ->
                    val createdAt = scanned + (index * 3 + track).seconds / 100
                    createSongData(album = album, track = track).copy(
                        path = "jellyfin://item/$index-$track",
                        externalId = "$index-$track",
                        mediaProvider = MediaProviderType.Jellyfin,
                        lastModified = createdAt,
                        dateAdded = createdAt
                    )
                }
            }.toTypedArray()
        )

        repository.recentlyAddedAlbums(limit = 3) shouldBe listOf(key("album 40"), key("album 39"), key("album 38"))
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
    fun `excluded songs are left out`() = runTest {
        insert(createSongData(album = "Hidden", track = 1).copy(excluded = true, lastCompleted = now))

        repository.recentlyCompletedAlbums(10).shouldBeEmpty()
        repository.songCount().first() shouldBe 0
    }

    @Test
    fun `an album whose songs are all under the minimum track length is left out, and its songs aren't counted`() = runTest {
        minimum.value = MinTrackLength.ThirtySeconds
        insert(
            createSongData(album = "Ringtones", track = 1).copy(duration = 4_000, dateAdded = now),
            createSongData(album = "Ringtones", track = 2).copy(duration = 6_000, dateAdded = now),
            createSongData(album = "Blue", albumArtist = "Joni Mitchell", track = 1).copy(dateAdded = now - 1.days),
            createSongData(album = "Blue", albumArtist = "Joni Mitchell", track = 2).copy(duration = 9_000, dateAdded = now - 1.days)
        )
        val repository = repository

        val albums = repository.albums(repository.recentlyAddedAlbums(limit = 10))
        albums.map { it.name } shouldBe listOf("Blue")
        albums.single().songCount shouldBe 1
        repository.albumArtists(listOf(AlbumArtistGroupKey("artist"))).shouldBeEmpty()
        repository.songCount().first() shouldBe 1
    }

    @Test
    fun `genre counts are the Genres screen's, minimum track length and all`() = runTest {
        minimum.value = MinTrackLength.TenSeconds
        insert(
            createSongData(album = "A", track = 1).copy(genres = listOf("Jazz", "Soul")),
            createSongData(album = "A", track = 2).copy(genres = listOf("Jazz"), duration = 3_000),
            createSongData(album = "B", track = 1).copy(genres = listOf("Soul")),
            createSongData(album = "C", track = 1).copy(genres = listOf("Spoken"), duration = 5_000)
        )
        val (repository, genres) = repositories()

        repository.genres() shouldBe genres.getGenres(GenreQuery.All()).first()
        repository.genres().map { it.name to it.songCount }.sortedBy { it.first } shouldBe listOf("Jazz" to 1, "Soul" to 2)
        genres.getGenreCoverSongs("Jazz", limit = 4).first().map { it.track } shouldBe listOf(1)
        genres.getGenreCoverSongs("Spoken", limit = 4).first().shouldBeEmpty()
    }
}
