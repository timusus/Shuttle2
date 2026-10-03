package com.simplecityapps.localmediaprovider.local.repository

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.localmediaprovider.local.data.room.dao.toSong
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.database.trackingIdentityChanges
import com.simplecityapps.localmediaprovider.local.data.room.entity.SONG_IDENTITY_QUERY
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.mediaprovider.SongDiff
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.MinTrackLength
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.SongSortOrder
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Song reads against a real (in-memory) database, recording the SQL each one runs. */
@RunWith(AndroidJUnit4::class)
class LocalSongRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val songQueries = CopyOnWriteArrayList<String>()
    private val database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java).trackingIdentityChanges()
        .allowMainThreadQueries()
        .setQueryCallback(RoomDatabase.QueryCallback { sql, _ -> if (sql.contains("FROM songs") || sql.contains("UPDATE songs")) songQueries += sql }, Executor(Runnable::run))
        .build()

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `songs by id are read by id, not from the whole library`() = runTest {
        val repository = LocalSongRepository(backgroundScope, database.songDataDao(), database.libraryAlbumIndex())
        val songs = insertSongs((1..5).map { index -> "Song $index" })
        songQueries.clear()

        val restored = repository.getSongs(SongQuery.SongIds(listOf(songs[3].id, songs[1].id, songs[3].id))).first()

        restored.orEmpty().map(Song::name) shouldContainExactlyInAnyOrder listOf("Song 2", "Song 4")
        songQueries.isNotEmpty() shouldBe true
        // Besides the album identity columns (#637), which an album is decided over
        songQueries.filterNot { sql -> sql.contains("WHERE id IN") || sql == SONG_IDENTITY_QUERY } shouldBe emptyList()
    }

    @Test
    fun `artists' songs are read by id, not from the whole library`() = runTest {
        val repository = LocalSongRepository(backgroundScope, database.songDataDao(), database.libraryAlbumIndex())
        database.songDataDao().insert(listOf(songData("Song 1", "Blur"), songData("Song 2", "Blur"), songData("Song 3", "Oasis"), songData("Song 4", "Pulp")))
        val library = repository.loadSongs(SongQuery.All())
        val artists = listOf("Blur", "Oasis").map { artist -> SongQuery.ArtistGroupKey(library.first { it.albumArtist == artist }.albumArtistGroupKey) }
        songQueries.clear()

        val songs = repository.loadSongs(SongQuery.ArtistGroupKeys(artists))

        songs.map(Song::name) shouldContainExactlyInAnyOrder listOf("Song 1", "Song 2", "Song 3")
        // Besides the album identity columns the index is built from
        songQueries.filterNot { sql -> sql.contains("WHERE id IN") || sql == SONG_IDENTITY_QUERY } shouldBe emptyList()
    }

    @Test
    fun `more ids than SQLite binds in one statement are all read`() = runTest {
        val repository = LocalSongRepository(backgroundScope, database.songDataDao(), database.libraryAlbumIndex())
        val songs = insertSongs((1..1_500).map { index -> "Song $index" })

        val restored = repository.getSongs(SongQuery.SongIds(songs.map(Song::id))).first()

        restored.orEmpty().map(Song::id) shouldContainExactlyInAnyOrder songs.map(Song::id)
    }

    @Test
    fun `excluded songs are left out of songs by id`() = runTest {
        val repository = LocalSongRepository(backgroundScope, database.songDataDao(), database.libraryAlbumIndex())
        val (kept, excluded) = insertSongs(listOf("Kept", "Excluded"))
        repository.setExcluded(listOf(excluded), true)

        repository.getSongs(SongQuery.SongIds(listOf(kept.id, excluded.id))).first().orEmpty().map(Song::name) shouldBe listOf("Kept")
    }

    @Test
    fun `no ids reads nothing`() = runTest {
        val repository = LocalSongRepository(backgroundScope, database.songDataDao(), database.libraryAlbumIndex())
        insertSongs(listOf("Song"))
        songQueries.clear()

        repository.getSongs(SongQuery.SongIds(emptyList())).first() shouldBe emptyList()
        songQueries shouldBe emptyList()
    }

    @Test
    fun `a library query comes in its sort order`() = runTest {
        val repository = LocalSongRepository(backgroundScope, database.songDataDao(), database.libraryAlbumIndex())
        insertSongs(listOf("Cherry", "Apple", "Banana"))

        val sorted = repository.getSongs(SongQuery.All(sortOrder = SongSortOrder.SongName)).filterNotNull().first()

        sorted.map(Song::name) shouldBe listOf("Apple", "Banana", "Cherry")
    }

    @Test
    fun `a new song's date added is its modification time, and later updates keep it`() = runTest {
        val repository = LocalSongRepository(backgroundScope, database.songDataDao(), database.libraryAlbumIndex())
        val modified = Instant.fromEpochMilliseconds(1_700_000_000_000)
        val template = songData("Template").toSong()
        repository.insert(listOf(template.copy(lastModified = modified, dateAdded = null)), MediaProviderType.Shuttle)
        val inserted = repository.loadSongs(SongQuery.All()).single()

        repository.update(inserted.copy(name = "Retagged", lastModified = modified + 1.days))

        repository.loadSongs(SongQuery.All()).single().run {
            name shouldBe "Retagged"
            dateAdded shouldBe modified
        }
    }

    @Test
    fun `a remote song's server date is kept on insert and replaces an older import stamp on update`() = runTest {
        val repository = LocalSongRepository(backgroundScope, database.songDataDao(), database.libraryAlbumIndex())
        val importStamp = Instant.fromEpochMilliseconds(1_750_000_000_000)
        val serverDate = Instant.fromEpochMilliseconds(1_600_000_000_000)
        val template = songData("Template").toSong()
        repository.insert(listOf(template.copy(path = "jellyfin://item/1", dateAdded = serverDate)), MediaProviderType.Jellyfin)
        repository.insert(listOf(template.copy(path = "jellyfin://item/2", dateAdded = importStamp)), MediaProviderType.Jellyfin)
        val (kept, stale) = repository.loadSongs(SongQuery.All()).sortedBy(Song::path)
        kept.dateAdded shouldBe serverDate

        repository.insertUpdateAndDelete(
            inserts = emptyList(),
            updates = listOf(stale.copy(dateAdded = serverDate)),
            deletes = emptyList(),
            mediaProviderType = MediaProviderType.Jellyfin
        )

        repository.loadSongs(SongQuery.All()).map(Song::dateAdded) shouldBe listOf(serverDate, serverDate)
    }

    @Test
    fun `a re-import fills the raw tags of songs stored before they were read, in place, keeping each id`() = runTest {
        val repository = LocalSongRepository(backgroundScope, database.songDataDao(), database.libraryAlbumIndex())
        val stored = insertSongs(listOf("First", "Second"))
        stored.map(Song::albumArtists) shouldBe listOf(null, null)

        val reread = stored.map { song ->
            song.copy(
                id = 0,
                albumArtists = listOf("Various Artists"),
                artistsTag = listOf("A", "B"),
                artistDisplay = "A feat. B",
                compilation = true,
                mbTrackId = "0f4e3c5a-1111-4c1e-9c2b-000000000001",
                mbArtistIds = listOf("0f4e3c5a-2222-4c1e-9c2b-000000000002"),
                serverAlbumId = "album-1",
                serverArtistIds = emptyList()
            )
        }
        val diff = SongDiff(stored, reread).apply()
        diff.inserts shouldBe emptyList()
        repository.insertUpdateAndDelete(inserts = diff.inserts, updates = diff.updates, deletes = diff.deletes, mediaProviderType = MediaProviderType.Shuttle)

        val updated = repository.loadSongs(SongQuery.All()).sortedBy(Song::id)
        updated.map(Song::id) shouldBe stored.map(Song::id)
        updated.forEach { song ->
            song.albumArtists shouldBe listOf("Various Artists")
            song.artistsTag shouldBe listOf("A", "B")
            song.artistDisplay shouldBe "A feat. B"
            song.compilation shouldBe true
            song.mbTrackId shouldBe "0f4e3c5a-1111-4c1e-9c2b-000000000001"
            song.mbArtistIds shouldBe listOf("0f4e3c5a-2222-4c1e-9c2b-000000000002")
            song.serverAlbumId shouldBe "album-1"
            // Read and untagged (empty) stays distinct from never read (null)
            song.serverArtistIds shouldBe emptyList()
            song.serverAlbumArtistIds shouldBe null
        }
    }

    @Test
    fun `a track played through updates its position and play count in one write`() = runTest {
        val repository = LocalSongRepository(backgroundScope, database.songDataDao(), database.libraryAlbumIndex())
        val song = insertSongs(listOf("Song")).single()
        songQueries.clear()

        repository.recordPlayedThrough(song.copy(duration = 180_000))

        songQueries.count { sql -> sql.contains("UPDATE songs") } shouldBe 1
        val updated = database.songDataDao().get().single().toSong()
        updated.playbackPosition shouldBe 180_000
        updated.playCount shouldBe 1
    }

    @Test
    fun `a metadata write reports the songs it updated, and a play count or position write doesn't`() = runTest {
        val repository = LocalSongRepository(backgroundScope, database.songDataDao(), database.libraryAlbumIndex())
        val (first, second, third) = insertSongs(listOf("First", "Second", "Third"))
        val updates = mutableListOf<Set<Long>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.updatedSongIds.toList(updates) }

        repository.update(first.copy(name = "Retagged"))
        repository.update(listOf(second.copy(name = "Retagged")))
        repository.insertUpdateAndDelete(inserts = emptyList(), updates = listOf(third.copy(name = "Rescanned")), deletes = emptyList(), mediaProviderType = MediaProviderType.Shuttle)
        repository.insertUpdateAndDelete(inserts = emptyList(), updates = emptyList(), deletes = listOf(first), mediaProviderType = MediaProviderType.Shuttle)
        repository.recordPlayedThrough(second)
        repository.setPlaybackPosition(second, 1_000)

        updates shouldBe listOf(setOf(first.id), setOf(second.id), setOf(third.id))
    }

    @Test
    fun `a modification time in the future counts as added now`() = runTest {
        val repository = LocalSongRepository(backgroundScope, database.songDataDao(), database.libraryAlbumIndex())
        val template = songData("Template").toSong()
        repository.insert(listOf(template.copy(lastModified = Clock.System.now() + 365.days, dateAdded = null)), MediaProviderType.Shuttle)

        repository.loadSongs(SongQuery.All()).single().dateAdded!! shouldBeLessThanOrEqualTo Clock.System.now()
    }

    @Test
    fun `a favourite keeps the time it was first made one, and unfavouriting clears it`() = runTest {
        val repository = LocalSongRepository(backgroundScope, database.songDataDao(), database.libraryAlbumIndex())
        val (first, second) = insertSongs(listOf("First", "Second"))

        repository.setFavourite(listOf(first), true)
        val favouritedAt = repository.loadSongs(SongQuery.Favourites).single().favouritedAt!!
        repository.setFavourite(listOf(first, second), true)

        repository.loadSongs(SongQuery.All()).associate { song -> song.id to song.favouritedAt }.run {
            getValue(first.id) shouldBe favouritedAt
            (getValue(second.id) != null) shouldBe true
        }

        repository.setFavourite(listOf(first), false)

        repository.getFavouriteSongIds().first() shouldBe setOf(second.id)
    }

    @Test
    fun `undoing a remove restores the song's original favourited time, not now (#564)`() = runTest {
        val repository = LocalSongRepository(backgroundScope, database.songDataDao(), database.libraryAlbumIndex())
        val song = insertSongs(listOf("Song")).single()

        repository.setFavourite(listOf(song), true)
        val originalFavouritedSong = repository.loadSongs(SongQuery.Favourites).single()
        repository.setFavourite(listOf(song), false)

        // Undo: FavouriteSongs resolves the selection before removal, so the Undo carries the song with its original favouritedAt.
        repository.setFavourite(listOf(originalFavouritedSong), true)

        repository.loadSongs(SongQuery.Favourites).single().favouritedAt shouldBe originalFavouritedSong.favouritedAt
    }

    @Test
    fun `more favourites than SQLite binds in one statement are all set`() = runTest {
        val repository = LocalSongRepository(backgroundScope, database.songDataDao(), database.libraryAlbumIndex())
        val songs = insertSongs((1..1_500).map { index -> "Song $index" })

        repository.setFavourite(songs, true)

        repository.getFavouriteSongIds().first() shouldBe songs.map(Song::id).toSet()
    }

    @Test
    fun `a rescan or retag keeps a song a favourite`() = runTest {
        val repository = LocalSongRepository(backgroundScope, database.songDataDao(), database.libraryAlbumIndex())
        val song = insertSongs(listOf("Song")).single()
        repository.setFavourite(listOf(song), true)

        repository.update(song.copy(name = "Retagged"))
        repository.insertUpdateAndDelete(inserts = emptyList(), updates = listOf(song.copy(name = "Rescanned")), deletes = emptyList(), mediaProviderType = MediaProviderType.Shuttle)

        repository.loadSongs(SongQuery.All()).single().run {
            name shouldBe "Rescanned"
            isFavourite shouldBe true
        }
    }

    private suspend fun insertSongs(names: List<String>): List<Song> {
        database.songDataDao().insert(names.map(::songData))
        return database.songDataDao().get().map { songData -> songData.toSong() }.sortedBy(Song::id)
    }

    @Test
    fun `a minimum track length hides shorter songs from queries and keeps one exactly that long`() = runTest {
        val minimum = MutableStateFlow(MinTrackLength.ThirtySeconds)
        val repository = LocalSongRepository(backgroundScope, database.songDataDao(), database.libraryAlbumIndex(), minimum)
        database.songDataDao().insert(listOf(songData("Short", duration = 29_999), songData("Exact", duration = 30_000), songData("Long", duration = 200_000), songData("Unknown", duration = 0)))

        repository.loadSongs(SongQuery.All()).map(Song::name) shouldContainExactlyInAnyOrder listOf("Exact", "Long", "Unknown")
        repository.getSongs(SongQuery.All()).filterNotNull().first().map(Song::name) shouldContainExactlyInAnyOrder listOf("Exact", "Long", "Unknown")
        repository.loadSongs(SongQuery.Search("Short")) shouldBe emptyList()
    }

    @Test
    fun `changing the minimum track length re-emits the library`() = runTest {
        val minimum = MutableStateFlow(MinTrackLength.Off)
        val repository = LocalSongRepository(backgroundScope, database.songDataDao(), database.libraryAlbumIndex(), minimum)
        database.songDataDao().insert(listOf(songData("Short", duration = 5_000), songData("Long", duration = 200_000)))
        val seen = mutableListOf<List<String?>>()

        val afterChange = repository.getSongs(SongQuery.All())
            .filterNotNull()
            .map { songs -> songs.map(Song::name) }
            .onEach { names ->
                seen.add(names)
                minimum.value = MinTrackLength.TenSeconds
            }
            .first { names -> "Short" !in names }

        seen.first() shouldContainExactlyInAnyOrder listOf("Short", "Long")
        afterChange shouldBe listOf("Long")
    }

    @Test
    fun `songs asked for by id are kept however short, so playlists and the queue still hold them`() = runTest {
        val repository = LocalSongRepository(backgroundScope, database.songDataDao(), database.libraryAlbumIndex(), MutableStateFlow(MinTrackLength.SixtySeconds))
        database.songDataDao().insert(listOf(songData("Short", duration = 5_000)))
        val short = database.songDataDao().get().single().toSong()

        repository.loadSongs(SongQuery.SongIds(listOf(short.id))).map(Song::name) shouldBe listOf("Short")
        repository.getSongs(SongQuery.SongIds(listOf(short.id))).first().orEmpty().map(Song::name) shouldBe listOf("Short")
    }

    @Test
    fun `album and artist key queries leave out songs under the minimum`() = runTest {
        val repository = LocalSongRepository(backgroundScope, database.songDataDao(), database.libraryAlbumIndex(), MutableStateFlow(MinTrackLength.ThirtySeconds))
        database.songDataDao().insert(listOf(songData("Intro", duration = 8_000), songData("Song", duration = 200_000)))
        val album = SongQuery.AlbumGroupKey(repository.loadSongs(SongQuery.All()).single().albumGroupKey)
        val artist = SongQuery.ArtistGroupKey(album.key?.albumArtistGroupKey)

        repository.loadSongs(album).map(Song::name) shouldBe listOf("Song")
        repository.loadSongs(SongQuery.AlbumGroupKeys(listOf(album))).map(Song::name) shouldBe listOf("Song")
        repository.loadSongs(SongQuery.ArtistGroupKeys(listOf(artist))).map(Song::name) shouldBe listOf("Song")
        repository.getSongs(album).filterNotNull().first().map(Song::name) shouldBe listOf("Song")
    }

    @Test
    fun `albums and album artists are built from the songs that pass the minimum`() = runTest {
        val minimum = MutableStateFlow(MinTrackLength.SixtySeconds)
        val albums = LocalAlbumRepository(backgroundScope, database.songDataDao(), minimum)
        val artists = LocalAlbumArtistRepository(backgroundScope, database.songDataDao(), minimum)
        database.songDataDao().insert(listOf(songData("Ringtone", "Voice", duration = 4_000), songData("Song", "Band", duration = 240_000)))

        albums.getAlbums(AlbumQuery.All()).first().map { album -> album.albumArtist } shouldBe listOf("Band")
        artists.getAlbumArtists(AlbumArtistQuery.All()).first().map { artist -> artist.name } shouldBe listOf("Band")
    }

    private fun songData(
        name: String,
        artist: String = "Artist",
        duration: Int = 180_000
    ) = SongData(
        name = name,
        track = 1,
        disc = 1,
        duration = duration,
        year = null,
        genres = emptyList(),
        path = "/music/$name.mp3",
        albumArtist = artist,
        artists = listOf(artist),
        album = "Album",
        size = 0,
        mimeType = "audio/mpeg",
        lastModified = Instant.fromEpochMilliseconds(0),
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )
}
