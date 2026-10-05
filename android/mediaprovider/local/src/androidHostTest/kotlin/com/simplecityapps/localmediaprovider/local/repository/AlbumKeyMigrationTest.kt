package com.simplecityapps.localmediaprovider.local.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.entity.PinnedCollectionData
import com.simplecityapps.localmediaprovider.local.data.room.entity.PinnedCollectionData.CollectionType
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlayEventData
import com.simplecityapps.localmediaprovider.local.data.room.entity.ResumePointData
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** The moves of the stored album keys: those from before the album identity rule (#637), over rows as #633 stored them, and those from before the local artist split (#880). */
@RunWith(AndroidJUnit4::class)
class AlbumKeyMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val preferences = GeneralPreferenceManager(InMemoryKeyValueStore())
    private val migration = AlbumKeyMigration(database.songDataDao(), database.playEventDao(), database.resumePointDao(), database.pinnedCollectionDao(), preferences)

    private val drive = AlbumGroupKey("drive ost", AlbumArtistGroupKey("various artists"), "dir:/music/Drive")
    private val blue = AlbumGroupKey("blue", AlbumArtistGroupKey("joni mitchell"), "mb:b1")
    private val low = AlbumGroupKey("low", AlbumArtistGroupKey("david bowie"))

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insertLibrary() {
        database.songDataDao().insert(
            listOf(
                // An untagged album of several artists, once an album per track artist
                createSongData(album = "Drive OST", track = 1).copy(albumArtist = null, artists = listOf("Kavinsky"), path = "/music/Drive/1.mp3"),
                createSongData(album = "Drive OST", track = 2).copy(albumArtist = null, artists = listOf("College"), path = "/music/Drive/2.mp3"),
                // A tagged album that's now keyed by its MusicBrainz id
                createSongData(album = "Blue", albumArtist = "Joni Mitchell").copy(mbAlbumId = "b1"),
                // An album whose key hasn't changed
                createSongData(album = "Low", albumArtist = "David Bowie")
            )
        )
    }

    // Keys as #633 wrote them: "=" and each part, the album's artist then its name, joined by a unit separator
    private fun oldAlbumId(
        artist: String,
        album: String
    ) = "=$artist\u001F=$album"

    private suspend fun insertEvent(
        type: String,
        id: String
    ) {
        database.playEventDao().insert(PlayEventData(MediaProviderType.Shuttle, "/music/x.mp3", Instant.fromEpochMilliseconds(0), 1, true, 8, 3, type, id))
    }

    private suspend fun storedContexts(): List<Pair<String, String?>> = database.query("SELECT contextType, contextId FROM play_events", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1)) }
    }

    @Test
    fun `play history moves to the albums and artists its songs belong to now, keeping what it can't map`() = runTest {
        insertLibrary()
        insertEvent(PlayContext.TYPE_ALBUM, oldAlbumId("kavinsky", "drive ost"))
        insertEvent(PlayContext.TYPE_ALBUM, oldAlbumId("college", "drive ost"))
        insertEvent(PlayContext.TYPE_ALBUM, oldAlbumId("joni mitchell", "blue"))
        insertEvent(PlayContext.TYPE_ALBUM, oldAlbumId("david bowie", "low"))
        insertEvent(PlayContext.TYPE_ALBUM, oldAlbumId("nobody", "gone"))
        insertEvent(PlayContext.TYPE_ALBUM_ARTIST, "=kavinsky")
        insertEvent(PlayContext.TYPE_GENRE, "Jazz")

        migration.migrateIfDue(songTagsCurrent = true)

        storedContexts() shouldContainExactlyInAnyOrder listOf(
            PlayContext.TYPE_ALBUM to drive.encode(),
            PlayContext.TYPE_ALBUM to drive.encode(),
            PlayContext.TYPE_ALBUM to blue.encode(),
            PlayContext.TYPE_ALBUM to oldAlbumId("david bowie", "low"),
            PlayContext.TYPE_ALBUM to oldAlbumId("nobody", "gone"),
            PlayContext.TYPE_ALBUM_ARTIST to PlayContext.AlbumArtist(AlbumArtistGroupKey("various artists")).id,
            PlayContext.TYPE_GENRE to "Jazz"
        )
        // Each moved key names an album the album and artist repositories have
        val songDao = database.songDataDao()
        val albums = LocalAlbumRepository(backgroundScope, songDao).getAlbums(AlbumQuery.All()).first().map { it.groupKey }
        albums shouldContainExactlyInAnyOrder listOf(drive, blue, low)
        LocalAlbumArtistRepository(backgroundScope, songDao).getAlbumArtists(AlbumArtistQuery.All()).first().map { it.groupKey } shouldContainExactlyInAnyOrder
            listOf(AlbumArtistGroupKey("various artists"), AlbumArtistGroupKey("joni mitchell"), AlbumArtistGroupKey("david bowie"))
    }

    @Test
    fun `pinned albums move to their keys now, keeping what can't be mapped`() = runTest {
        insertLibrary()
        val pinned = database.pinnedCollectionDao()
        pinned.insert(PinnedCollectionData(CollectionType.Album, oldAlbumId("joni mitchell", "blue"), MediaProviderType.Shuttle))
        pinned.insert(PinnedCollectionData(CollectionType.Album, low.encode(), MediaProviderType.Shuttle))
        pinned.insert(PinnedCollectionData(CollectionType.Album, "not a key", MediaProviderType.Shuttle))
        pinned.insert(PinnedCollectionData(CollectionType.Playlist, "7", MediaProviderType.Shuttle))

        migration.migrateIfDue(songTagsCurrent = true)

        pinned.getAll().first().map { it.collectionType to it.collectionId } shouldContainExactlyInAnyOrder listOf(
            CollectionType.Album to blue.encode(),
            CollectionType.Album to low.encode(),
            CollectionType.Album to "not a key",
            CollectionType.Playlist to "7"
        )
    }

    @Test
    fun `a move run twice, as after a run cut short, leaves one pin per album`() = runTest {
        insertLibrary()
        val pinned = database.pinnedCollectionDao()
        // Two keys from before the rule that name one album now
        pinned.insert(PinnedCollectionData(CollectionType.Album, oldAlbumId("kavinsky", "drive ost"), MediaProviderType.Shuttle))
        pinned.insert(PinnedCollectionData(CollectionType.Album, oldAlbumId("college", "drive ost"), MediaProviderType.Shuttle))
        insertEvent(PlayContext.TYPE_ALBUM, oldAlbumId("kavinsky", "drive ost"))

        migration.moveToIdentityRule()
        migration.moveToIdentityRule()

        pinned.getAll().first().map { it.collectionId } shouldBe listOf(drive.encode())
        storedContexts() shouldBe listOf(PlayContext.TYPE_ALBUM to drive.encode())
    }

    @Test
    fun `the move waits for current tags, then runs once`() = runTest {
        insertLibrary()
        insertEvent(PlayContext.TYPE_ALBUM, oldAlbumId("joni mitchell", "blue"))

        migration.migrateIfDue(songTagsCurrent = false)
        storedContexts() shouldBe listOf(PlayContext.TYPE_ALBUM to oldAlbumId("joni mitchell", "blue"))

        migration.migrateIfDue(songTagsCurrent = true)
        storedContexts() shouldBe listOf(PlayContext.TYPE_ALBUM to blue.encode())
        preferences.albumKeysVersion shouldBe AlbumKeyMigration.ALBUM_KEYS_VERSION

        insertEvent(PlayContext.TYPE_ALBUM, oldAlbumId("joni mitchell", "blue"))
        migration.migrateIfDue(songTagsCurrent = true)
        storedContexts().count { it.second == blue.encode() } shouldBe 1
    }

    private val duets = AlbumGroupKey("duets", AlbumArtistGroupKey("ann"))
    private val mixed = AlbumGroupKey("mixed", AlbumArtistGroupKey("cat"))

    // Local songs as stored before #880, each holding its whole ARTIST tag, and a server song the split never touches
    private suspend fun insertUnsplitLibrary() {
        database.songDataDao().insert(
            listOf(
                createSongData(album = "Duets", track = 1).copy(albumArtist = null, artists = listOf("Ann / Bob"), path = "/music/Duets/1.mp3"),
                createSongData(album = "Duets", track = 2).copy(albumArtist = null, artists = listOf("Ann / Bob"), path = "/music/Duets/2.mp3"),
                // A folder of two artists, one album once split
                createSongData(album = "Mixed", track = 1).copy(albumArtist = null, artists = listOf("Cat | Dan"), path = "/music/Mixed/1.mp3"),
                createSongData(album = "Mixed", track = 2).copy(albumArtist = null, artists = listOf("Cat"), path = "/music/Mixed/2.mp3"),
                createSongData(album = "Live", track = 1).copy(albumArtist = null, artists = listOf("Eve / Fay"), path = "jellyfin://item/1", mediaProvider = MediaProviderType.Jellyfin),
                createSongData(album = "Low", albumArtist = "David Bowie")
            )
        )
    }

    private val unsplitDuets = AlbumGroupKey("duets", AlbumArtistGroupKey("ann / bob"))
    private val unsplitMixed = AlbumGroupKey("mixed", AlbumArtistGroupKey("various artists"), "dir:/music/Mixed")
    private val live = AlbumGroupKey("live", AlbumArtistGroupKey("eve / fay"))

    private suspend fun insertResumePoint(
        type: String,
        id: String,
        updatedAt: Long = 0,
        positionMs: Long = 1_000
    ) {
        database.resumePointDao().upsert(ResumePointData(type, id, MediaProviderType.Shuttle, "/music/x.mp3", positionMs, 0, 2, false, false, Instant.fromEpochMilliseconds(updatedAt)))
    }

    private suspend fun storedResumeContexts(): List<Pair<String, String>> = database.query("SELECT contextType, contextId FROM resume_points", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1)) }
    }

    private suspend fun storedArtists(): List<List<String>> = database.songDataDao().identityData().map { it.artists }

    @Test
    fun `the local artist split moves play history, resume points and pinned albums to the albums their songs belong to now`() = runTest {
        insertUnsplitLibrary()
        insertEvent(PlayContext.TYPE_ALBUM, unsplitDuets.encode())
        insertEvent(PlayContext.TYPE_ALBUM, unsplitMixed.encode())
        insertEvent(PlayContext.TYPE_ALBUM, live.encode())
        insertEvent(PlayContext.TYPE_ALBUM, low.encode())
        insertEvent(PlayContext.TYPE_ALBUM_ARTIST, PlayContext.AlbumArtist(AlbumArtistGroupKey("ann / bob")).id!!)
        insertResumePoint(PlayContext.TYPE_ALBUM, unsplitDuets.encode())
        insertResumePoint(PlayContext.TYPE_ALBUM_ARTIST, PlayContext.AlbumArtist(AlbumArtistGroupKey("ann / bob")).id!!)
        insertResumePoint(PlayContext.TYPE_ALBUM, low.encode())
        val pinned = database.pinnedCollectionDao()
        pinned.insert(PinnedCollectionData(CollectionType.Album, unsplitMixed.encode(), MediaProviderType.Shuttle))
        pinned.insert(PinnedCollectionData(CollectionType.Album, live.encode(), MediaProviderType.Jellyfin))

        migration.splitLocalArtists()

        storedContexts() shouldContainExactlyInAnyOrder listOf(
            PlayContext.TYPE_ALBUM to duets.encode(),
            PlayContext.TYPE_ALBUM to mixed.encode(),
            PlayContext.TYPE_ALBUM to live.encode(),
            PlayContext.TYPE_ALBUM to low.encode(),
            PlayContext.TYPE_ALBUM_ARTIST to PlayContext.AlbumArtist(AlbumArtistGroupKey("ann")).id
        )
        storedResumeContexts() shouldContainExactlyInAnyOrder listOf(
            PlayContext.TYPE_ALBUM to duets.encode(),
            PlayContext.TYPE_ALBUM_ARTIST to PlayContext.AlbumArtist(AlbumArtistGroupKey("ann")).id!!,
            PlayContext.TYPE_ALBUM to low.encode()
        )
        pinned.getAll().first().map { it.collectionId } shouldContainExactlyInAnyOrder listOf(mixed.encode(), live.encode())
        // The local songs hold their artists as a read of their files does now; the server song is left as it is
        storedArtists() shouldContainExactlyInAnyOrder listOf(
            listOf("Ann", "Bob"),
            listOf("Ann", "Bob"),
            listOf("Cat", "Dan"),
            listOf("Cat"),
            listOf("Eve / Fay"),
            listOf("Artist")
        )
        // Each moved key names an album the album repository has
        LocalAlbumRepository(backgroundScope, database.songDataDao()).getAlbums(AlbumQuery.All()).first().map { it.groupKey } shouldContainExactlyInAnyOrder
            listOf(duets, mixed, live, low)
    }

    private suspend fun storedResumePositions(): List<Long> = database.query("SELECT positionMs FROM resume_points", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
    }

    @Test
    fun `a resume point moved onto an album that has one leaves the newer`() = runTest {
        insertUnsplitLibrary()
        insertResumePoint(PlayContext.TYPE_ALBUM, unsplitDuets.encode(), updatedAt = 2, positionMs = 20)
        insertResumePoint(PlayContext.TYPE_ALBUM, duets.encode(), updatedAt = 1, positionMs = 10)

        migration.splitLocalArtists()

        storedResumeContexts() shouldBe listOf(PlayContext.TYPE_ALBUM to duets.encode())
        storedResumePositions() shouldBe listOf(20L)
    }

    @Test
    fun `a resume point moved onto a newer one leaves the newer`() = runTest {
        insertUnsplitLibrary()
        insertResumePoint(PlayContext.TYPE_ALBUM, unsplitDuets.encode(), updatedAt = 1, positionMs = 10)
        insertResumePoint(PlayContext.TYPE_ALBUM, duets.encode(), updatedAt = 2, positionMs = 20)

        migration.splitLocalArtists()

        storedResumeContexts() shouldBe listOf(PlayContext.TYPE_ALBUM to duets.encode())
        storedResumePositions() shouldBe listOf(20L)
    }

    @Test
    fun `the split run again, as after a run cut short, keeps what moved`() = runTest {
        insertUnsplitLibrary()
        insertEvent(PlayContext.TYPE_ALBUM, unsplitDuets.encode())
        database.pinnedCollectionDao().insert(PinnedCollectionData(CollectionType.Album, unsplitDuets.encode(), MediaProviderType.Shuttle))

        migration.splitLocalArtists()
        migration.splitLocalArtists()

        storedContexts() shouldBe listOf(PlayContext.TYPE_ALBUM to duets.encode())
        database.pinnedCollectionDao().getAll().first().map { it.collectionId } shouldBe listOf(duets.encode())
    }

    @Test
    fun `a library past the identity rule move only runs the split, once`() = runTest {
        insertUnsplitLibrary()
        preferences.albumKeysVersion = AlbumKeyMigration.IDENTITY_RULE_KEYS
        insertEvent(PlayContext.TYPE_ALBUM, unsplitDuets.encode())

        migration.migrateIfDue(songTagsCurrent = false)
        storedContexts() shouldBe listOf(PlayContext.TYPE_ALBUM to unsplitDuets.encode())

        migration.migrateIfDue(songTagsCurrent = true)
        storedContexts() shouldBe listOf(PlayContext.TYPE_ALBUM to duets.encode())
        preferences.albumKeysVersion shouldBe AlbumKeyMigration.ALBUM_KEYS_VERSION
    }
}
