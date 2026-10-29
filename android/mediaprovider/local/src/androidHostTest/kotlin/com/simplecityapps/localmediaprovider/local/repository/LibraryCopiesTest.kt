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
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** A library holding the same album in local files and on Emby, read through the repositories over a real database. */
@RunWith(AndroidJUnit4::class)
class LibraryCopiesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java).trackingIdentityChanges().allowMainThreadQueries().build()
    private val merge = MutableStateFlow(true)

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `an album held locally and on Emby lists each of its 12 songs once`() = runTest {
        insert(local(1..12) + emby(1..12))

        albumSongCounts() shouldBe listOf(12)
        songs().size shouldBe 12
        songs(SongQuery.AlbumGroupKey(songs().first().albumGroupKey)).size shouldBe 12
    }

    @Test
    fun `an album with 10 songs locally and 12 on Emby lists 12`() = runTest {
        insert(local(1..10) + emby(1..12))

        albumSongCounts() shouldBe listOf(12)
        songs().map { it.track } shouldContainExactlyInAnyOrder (1..12).toList()
        songs().filter { it.track!! <= 10 }.map { it.mediaProvider }.distinct() shouldBe listOf(MediaProviderType.Shuttle)
        songs().single { it.track == 12 }.copies shouldBe emptyList()
    }

    @Test
    fun `a source's own duplicates stay apart`() = runTest {
        insert(local(1..1) + local(1..1, folder = "Copy") + emby(1..1))

        songs().size shouldBe 2
    }

    @Test
    fun `merging off lists every copy as it does without merging`() = runTest {
        insert(local(1..12) + emby(1..12))
        merge.value = false

        albumSongCounts() shouldBe listOf(24)
        songs().size shouldBe 24
        songs().all { it.copies.isEmpty() } shouldBe true
    }

    @Test
    fun `playing either copy counts once for the song and splits back to each copy when merging stops`() = runTest {
        insert(local(1..1) + emby(1..1))
        val repository = songRepository()
        val merged = repository.loadSongs(SongQuery.All()).single()

        repository.recordPlayedThrough(merged)
        repository.recordPlayedThrough(merged.copies.single { it.mediaProvider == MediaProviderType.Emby })

        repository.loadSongs(SongQuery.All()).single().playCount shouldBe 2
        merge.value = false
        repository.loadSongs(SongQuery.All()).associate { it.mediaProvider to it.playCount } shouldBe mapOf(MediaProviderType.Shuttle to 1, MediaProviderType.Emby to 1)
    }

    @Test
    fun `a favourite copy marks the merged song and unfavouriting the song clears every copy`() = runTest {
        insert(local(1..1) + emby(1..1))
        val repository = songRepository()
        val merged = repository.loadSongs(SongQuery.All()).single()
        val emby = merged.copies.single { it.mediaProvider == MediaProviderType.Emby }

        repository.setFavourite(listOf(emby), true)
        repository.getFavouriteSongIds().first { it.isNotEmpty() } shouldContainExactlyInAnyOrder setOf(emby.id, merged.id)

        repository.setFavourite(listOf(repository.loadSongs(SongQuery.All()).single()), false)
        repository.getFavouriteSongIds().first { it.isEmpty() } shouldBe emptySet()
    }

    @Test
    fun `unfavouriting the copy a playlist holds clears the favourite on every copy`() = runTest {
        insert(local(1..1) + emby(1..1))
        val repository = songRepository()
        repository.setFavourite(listOf(repository.loadSongs(SongQuery.All()).single()), true)
        val embyId = database.songDataDao().getAll().first().single { it.mediaProvider == MediaProviderType.Emby }.id
        val held = repository.loadSongs(SongQuery.SongIds(listOf(embyId))).single()

        repository.setFavourite(listOf(held), false)

        repository.getFavouriteSongIds().first() shouldBe emptySet()
    }

    @Test
    fun `favouriting the copy a playlist holds favourites every copy`() = runTest {
        insert(local(1..1) + emby(1..1))
        val repository = songRepository()
        val embyId = database.songDataDao().getAll().first().single { it.mediaProvider == MediaProviderType.Emby }.id

        repository.setFavourite(repository.loadSongs(SongQuery.SongIds(listOf(embyId))), true)

        database.songDataDao().getFavouriteIds().first().size shouldBe 2
    }

    @Test
    fun `songs albums artists and favourites read one merge of the library`() = runTest {
        insert(local(1..12) + emby(1..12))
        var reads = 0
        val copies = copies(database.songDataDao().getAll().onEach { reads++ })

        songRepository(copies).getSongs(SongQuery.All()).filterNotNull().first().size shouldBe 12
        albumRepository(copies).getAlbums(AlbumQuery.All()).first { it.isNotEmpty() }.single().songCount shouldBe 12
        LocalAlbumArtistRepository(backgroundScope, UnconfinedTestDispatcher(testScheduler), copies.listedSongs).getAlbumArtists(AlbumArtistQuery.All()).first { it.isNotEmpty() }.single().songCount shouldBe 12
        songRepository(copies).getFavouriteSongIds().first() shouldBe emptySet()

        reads shouldBe 1
    }

    @Test
    fun `merging off reads favourites without waiting for the library`() = runTest {
        insert(local(1..1) + emby(1..1))
        merge.value = false
        val repository = songRepository(copies(library = flow { awaitCancellation() }))

        repository.setFavourite(repository.loadSongs(SongQuery.All()).take(1), true)

        repository.getFavouriteSongIds().first().size shouldBe 1
    }

    @Test
    fun `a query on favourites finds the merged song with the history of all its copies`() = runTest {
        insert(local(1..1) + emby(1..1))
        val repository = songRepository()
        val merged = repository.loadSongs(SongQuery.All()).single()
        val emby = merged.copies.single { it.mediaProvider == MediaProviderType.Emby }
        repository.recordPlayedThrough(merged.copies.single { it.mediaProvider == MediaProviderType.Shuttle })
        database.songDataDao().setFavourite(listOf(emby), true)

        val favourites = repository.getSongs(SongQuery.Favourites).filterNotNull().first { it.isNotEmpty() }

        favourites.map { it.id to it.playCount } shouldBe listOf(merged.id to 1)
        repository.loadSongs(SongQuery.Favourites).map { it.id to it.playCount } shouldBe listOf(merged.id to 1)
    }

    @Test
    fun `excluding a merged song or the copy a playlist holds excludes every copy`() = runTest {
        insert(local(1..2) + emby(1..2))
        val repository = songRepository()
        val (first, second) = repository.loadSongs(SongQuery.All())
        val heldEmby = repository.loadSongs(SongQuery.SongIds(listOf(second.copies.single { it.mediaProvider == MediaProviderType.Emby }.id))).single()

        repository.setExcluded(listOf(first, heldEmby), true)

        songs() shouldBe emptyList()
        repository.setExcluded(listOf(heldEmby), false)
        songs().map { it.track } shouldBe listOf(2)
    }

    @Test
    fun `editing a merged song's tags keeps each copy's own date added`() = runTest {
        val localAdded = Instant.fromEpochSeconds(2_000)
        val embyAdded = Instant.fromEpochSeconds(1_000)
        insert(local(1..1).map { it.copy(dateAdded = localAdded) } + emby(1..1).map { it.apply { dateAdded = embyAdded } })
        val repository = songRepository()
        val merged = repository.loadSongs(SongQuery.All()).single()

        repository.update(merged.copy(name = "Renamed"))

        database.songDataDao().getAll().first().associate { it.mediaProvider to it.dateAdded } shouldBe mapOf(MediaProviderType.Shuttle to localAdded, MediaProviderType.Emby to embyAdded)
    }

    @Test
    fun `a genre held locally and on Emby counts and lists each song once, with the history of all its copies`() = runTest {
        insert((local(1..12) + emby(1..12)).map { it.copy(genres = listOf("Electronic"), duration = 60_000) })
        val repository = songRepository()
        repository.loadSongs(SongQuery.All()).first().copies.forEach { copy -> repository.recordPlayedThrough(copy) }
        val genres = LocalGenreRepository(backgroundScope, UnconfinedTestDispatcher(testScheduler), database.songDataDao(), repository, database.libraryAlbumIndex(), copies = copies())

        genres.getGenres(GenreQuery.All()).first { it.isNotEmpty() }.single().let { genre -> genre.songCount to genre.duration } shouldBe (12 to 720_000)
        val genreSongs = genres.getSongsForGenres(listOf("Electronic"), SongQuery.All()).first { it.isNotEmpty() }
        genreSongs.size shouldBe 12
        genreSongs.single { it.track == 1 }.playCount shouldBe 2
        genres.getGenreCoverSongs("Electronic", limit = 4).first { it.isNotEmpty() }.size shouldBe 1
    }

    @Test
    fun `the library's song count counts a merged song once and every copy while merging is off`() = runTest {
        insert(local(1..12) + emby(1..12))
        val repository = songRepository()

        repository.countSongs().first() shouldBe 12
        merge.value = false
        repository.countSongs().first() shouldBe 24
        repository.countSongsByProvider().first() shouldBe mapOf(MediaProviderType.Shuttle to 12, MediaProviderType.Emby to 12)
    }

    @Test
    fun `merging off counts a genre's songs in every source`() = runTest {
        insert((local(1..12) + emby(1..12)).map { it.copy(genres = listOf("Electronic")) })
        merge.value = false
        val genres = LocalGenreRepository(backgroundScope, UnconfinedTestDispatcher(testScheduler), database.songDataDao(), songRepository(), database.libraryAlbumIndex(), copies = copies())

        genres.getGenres(GenreQuery.All()).first { it.isNotEmpty() }.single().songCount shouldBe 24
    }

    @Test
    fun `a playlist's copy is read as that exact copy`() = runTest {
        insert(local(1..1) + emby(1..1))
        val repository = songRepository()
        val emby = repository.loadSongs(SongQuery.All()).single().copies.single { it.mediaProvider == MediaProviderType.Emby }

        val held = repository.getSongs(SongQuery.SongIds(listOf(emby.id))).filterNotNull().first().single()

        held.id shouldBe emby.id
        held.mediaProvider shouldBe MediaProviderType.Emby
        held.copies shouldBe emptyList()
        repository.loadSongs(SongQuery.SongIds(listOf(emby.id))).single().id shouldBe emby.id
    }

    @Test
    fun `a restored queue's copy keeps its id and carries its whole group`() = runTest {
        insert(local(1..1) + emby(1..1))
        val repository = songRepository()
        val emby = repository.loadSongs(SongQuery.All()).single().copies.single { it.mediaProvider == MediaProviderType.Emby }
        val query = SongQuery.SongIds(listOf(emby.id), withCopies = true)

        val observed = repository.getSongs(query).filterNotNull().first().single()
        val loaded = repository.loadSongs(query).single()

        listOf(observed, loaded).forEach { held ->
            held.id shouldBe emby.id
            held.copies.map { it.mediaProvider } shouldContainExactlyInAnyOrder listOf(MediaProviderType.Shuttle, MediaProviderType.Emby)
        }
    }

    private fun TestScope.copies(library: Flow<List<Song>> = database.songDataDao().getAll()) = LibraryCopies(backgroundScope, UnconfinedTestDispatcher(testScheduler), library, merge)

    private fun TestScope.songRepository(copies: LibraryCopies = copies()) = LocalSongRepository(backgroundScope, UnconfinedTestDispatcher(testScheduler), database.songDataDao(), database.libraryAlbumIndex(), copies = copies)

    private suspend fun TestScope.songs(query: SongQuery = SongQuery.All()): List<Song> = songRepository().getSongs(query).filterNotNull().first()

    private suspend fun TestScope.albumSongCounts(): List<Int> = albumRepository().getAlbums(AlbumQuery.All()).first { it.isNotEmpty() }.map { it.songCount }

    private fun TestScope.albumRepository(copies: LibraryCopies = copies()) = LocalAlbumRepository(backgroundScope, UnconfinedTestDispatcher(testScheduler), copies.listedSongs)

    private suspend fun insert(songs: List<SongData>) {
        database.songDataDao().insert(songs)
    }

    private fun local(
        tracks: IntRange,
        folder: String = "Isles"
    ) = tracks.map { track -> createSongData(album = "Isles", albumArtist = "Bicep", track = track).copy(name = "Song $track", path = "/music/Bicep/$folder/$track.flac", mimeType = "audio/flac") }

    private fun emby(tracks: IntRange) = tracks.map { track ->
        createSongData(album = "Isles", albumArtist = "Bicep", track = track).copy(name = "Song $track", path = "emby://item/$track").apply {
            mediaProvider = MediaProviderType.Emby
            externalId = "item-$track"
        }
    }
}
