package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.localmediaprovider.local.data.room.migrations.ALL_MIGRATIONS
import com.simplecityapps.localmediaprovider.local.repository.LocalPlaylistRepository
import com.simplecityapps.localmediaprovider.local.repository.LocalSongRepository
import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.SongPathRemap
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.Date
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "legacy-saf-songs-import-test"

// The schema Play 1.0.10 (a3a79c54) shipped
private const val SCHEMA_1_0_10 = 40

private const val MUSIC_TREE = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
private const val ALBUM_TREE = "content://com.android.externalstorage.documents/tree/primary%3AMusic%2FAlbum"
private const val SD_CARD_TREE = "content://com.android.externalstorage.documents/tree/04B9-1208%3AMusic"
private const val OLD_SD_CARD_TREE = "content://com.android.externalstorage.documents/tree/1111-2222%3AMusic"

private const val FAVOURITES = 1L
private const val ROAD_TRIP = 2L

/**
 * An S2-provider library saved by 1.0.10, whose songs are keyed by SAF document URI, upgraded through every migration
 * and then imported by the MediaStore-based scanner: the songs keep their rows and everything that hangs off them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class LegacySafSongsImportTest {
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MediaDatabase::class.java)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var database: MediaDatabase

    // MediaStore's listing after the upgrade
    private val primarySong = file(id = 100, path = "/storage/emulated/0/Music/Album/Primary.mp3")
    private val sdCardSong = file(id = 101, path = "/storage/04B9-1208/Music/SD Card.flac")
    private val reformattedSdCardSong = file(id = 102, path = "/storage/3333-4444/Music/Reformatted.mp3", size = 7_000)
    private val primaryCopyOfReformatted = file(id = 103, path = "/storage/emulated/0/Music/Reformatted.mp3", size = 6_000)
    private val newSong = file(id = 104, path = "/storage/emulated/0/Music/New.mp3")
    private val mediaStore = listOf(primarySong, sdCardSong, reformattedSdCardSong, primaryCopyOfReformatted, newSong)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        helper.createDatabase(TEST_DB, SCHEMA_1_0_10).apply {
            insertSong(id = 1, path = "$MUSIC_TREE/document/primary%3AMusic%2FAlbum%2FPrimary.mp3", playCount = 5, lastPlayed = 1_750_000_000_000)
            insertSong(id = 2, path = "$SD_CARD_TREE/document/04B9-1208%3AMusic%2FSD%20Card.flac", playCount = 1, excluded = true)
            insertSong(id = 3, path = "$OLD_SD_CARD_TREE/document/1111-2222%3AMusic%2FReformatted.mp3", size = 7_000, playCount = 3)
            insertSong(id = 4, path = "$MUSIC_TREE/document/primary%3AMusic%2FDeleted.mp3", playCount = 9)
            // The same file as song 1, found again through a second, overlapping folder grant
            insertSong(id = 5, path = "$ALBUM_TREE/document/primary%3AMusic%2FAlbum%2FPrimary.mp3", playCount = 2)
            insertSong(id = 6, path = "/storage/emulated/0/Music/Other.mp3", provider = MediaProviderType.MediaStore, playCount = 4)

            execSQL("INSERT INTO playlists (id, name, sortOrder, mediaProvider, externalId) VALUES ($FAVOURITES, 'Favorites', 'Position', 'Shuttle', NULL)")
            execSQL("INSERT INTO playlists (id, name, sortOrder, mediaProvider, externalId) VALUES ($ROAD_TRIP, 'Road trip', 'Position', 'Shuttle', NULL)")
            listOf(FAVOURITES to 5L, ROAD_TRIP to 2L, ROAD_TRIP to 4L, ROAD_TRIP to 5L).forEachIndexed { index, (playlistId, songId) ->
                execSQL("INSERT INTO playlist_song_join (playlistId, songId, sortOrder) VALUES ($playlistId, $songId, $index)")
            }
            close()
        }
        database =
            Room.databaseBuilder(context, MediaDatabase::class.java, TEST_DB)
                .addMigrations(*ALL_MIGRATIONS)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        database.close()
        scope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `the first import after the upgrade keeps each matched song's row, plays, exclude flag and playlists`() = runBlocking<Unit> {
        import()

        val songs = shuttleSongs().associateBy { song -> song.path }
        songs.keys shouldBe mediaStore.map { file -> file.path }.toSet()
        songs.getValue(primarySong.path).run {
            id shouldBe 1
            playCount shouldBe 5
            lastPlayed shouldBe Date(1_750_000_000_000)
            name shouldBe "Primary (tags read after the upgrade)"
        }
        songs.getValue(sdCardSong.path).run {
            id shouldBe 2
            playCount shouldBe 1
            excluded shouldBe true
        }
        songs.getValue(reformattedSdCardSong.path).run {
            id shouldBe 3
            playCount shouldBe 3
        }
        songs.getValue(newSong.path).playCount shouldBe 0

        // Song 4's file is gone, so the import removed it with its playlist entry; song 5's entry moved to song 1. The
        // Favorites playlist became a flag on song 5 (#497), which song 1 keeps
        playlistEntries() shouldBe listOf(ROAD_TRIP to 2L, ROAD_TRIP to 1L)
        songs.getValue(primarySong.path).favouritedAt shouldNotBe null
        songs.getValue(sdCardSong.path).favouritedAt shouldBe null

        // The MediaStore provider's own copy is untouched
        database.songDataDao().get().single { song -> song.id == 6L }.playCount shouldBe 4
    }

    @Test
    fun `a later import finds nothing left to remap and changes nothing`() = runBlocking<Unit> {
        val provider = import()
        val afterFirstImport = database.songDataDao().get().map { song -> song.id to song.path }

        import(provider)

        provider.remaps.last().shouldBeEmpty()
        database.songDataDao().get().map { song -> song.id to song.path } shouldBe afterFirstImport
    }

    @Test
    fun `a remap that fails keeps the old songs for the next import`() = runBlocking<Unit> {
        import(FakeTaglibMediaProvider(mediaStore, failRemap = true))

        shuttleSongs().map { song -> song.id } shouldBe listOf(1L, 2L, 3L, 4L, 5L)
        // The Favorites entry is a flag since #497, so only Road trip's three entries remain
        playlistEntries().size shouldBe 3
    }

    @Test
    fun `a remap onto a path another provider's song already has succeeds, but onto the same provider's is skipped`() = runBlocking<Unit> {
        val remaps =
            listOf(
                // Song 6 holds this path as a MediaStore song, so it's no longer a conflict for a Shuttle remap (#420)
                SongPathRemap(songId = 2, path = "/storage/emulated/0/Music/Other.mp3"),
                SongPathRemap(songId = 3, path = "/gone/3.mp3"),
                // Song 1 already holds this path as a Shuttle song
                SongPathRemap(songId = 4, path = "$MUSIC_TREE/document/primary%3AMusic%2FAlbum%2FPrimary.mp3")
            )

        LocalSongRepository(scope, database.songDataDao()).remapPaths(remaps, MediaProviderType.Shuttle) shouldBe listOf(remaps[0], remaps[1])

        database.songDataDao().get().single { song -> song.id == 2L }.path shouldBe "/storage/emulated/0/Music/Other.mp3"
        database.songDataDao().get().single { song -> song.id == 4L }.path shouldBe "$MUSIC_TREE/document/primary%3AMusic%2FDeleted.mp3"
    }

    private suspend fun import(provider: FakeTaglibMediaProvider = FakeTaglibMediaProvider(mediaStore)): FakeTaglibMediaProvider {
        val importer =
            MediaImporter(
                context = context,
                songRepository = LocalSongRepository(scope, database.songDataDao()),
                playlistStore = LocalPlaylistRepository(context, scope, database.playlistDataDao(), database.playlistSongJoinDataDao(), database.songDataDao()),
                preferenceManager = GeneralPreferenceManager(context.getSharedPreferences("import-test", Context.MODE_PRIVATE))
            )
        importer.mediaProviders += provider
        importer.import()
        return provider
    }

    private suspend fun shuttleSongs(): List<SongData> = database.songDataDao().get().filter { song -> song.mediaProvider == MediaProviderType.Shuttle }.sortedBy { song -> song.id }

    private fun playlistEntries(): List<Pair<Long, Long>> = database.openHelper.readableDatabase.query("SELECT playlistId, songId FROM playlist_song_join ORDER BY sortOrder").use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getLong(0) to cursor.getLong(1)) }
    }

    /** The TagLib provider's remap, over a fixed MediaStore listing in place of the device's, with TagLib's reads faked. */
    private class FakeTaglibMediaProvider(
        private val files: List<MediaStoreAudioFile>,
        private val failRemap: Boolean = false
    ) : MediaProvider {
        override val type = MediaProviderType.Shuttle

        val remaps = mutableListOf<List<SongPathRemap>>()

        override suspend fun remapLegacySongs(existingSongs: List<Song>): List<SongPathRemap> {
            if (failRemap) error("MediaStore went away")
            return LegacySafSongs(primaryStoragePath = "/storage/emulated/0").remaps(existingSongs, files).also { remaps += it }
        }

        override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = flowOf(FlowEvent.Success(files.map { file -> file.toScannedSong() }))

        override fun findPlaylists(existingSongs: List<Song>): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>> = flowOf(FlowEvent.Success(emptyList()))

        private fun MediaStoreAudioFile.toScannedSong() = Song(
            id = 0,
            name = if (displayName == "Primary.mp3") "Primary (tags read after the upgrade)" else displayName,
            albumArtist = "Artist",
            artists = listOf("Artist"),
            album = "Album",
            track = 1,
            disc = 1,
            duration = duration!!.toInt(),
            date = null,
            genres = emptyList(),
            path = path,
            size = size,
            mimeType = mimeType!!,
            lastModified = Instant.fromEpochMilliseconds(lastModified),
            lastPlayed = null,
            lastCompleted = null,
            playCount = 0,
            playbackPosition = 0,
            blacklisted = false,
            mediaProvider = MediaProviderType.Shuttle,
            lyrics = null,
            grouping = null,
            bitRate = null,
            bitDepth = null,
            sampleRate = null,
            channelCount = null
        )
    }
}

private fun file(
    id: Long,
    path: String,
    size: Long = 5_000
) = MediaStoreAudioFile(id = id, path = path, displayName = path.substringAfterLast('/'), size = size, lastModified = 1_700_000_000_000, mimeType = "audio/mpeg", duration = 180_000)

private fun SupportSQLiteDatabase.insertSong(
    id: Long,
    path: String,
    size: Long = 5_000,
    playCount: Int = 0,
    lastPlayed: Long? = null,
    excluded: Boolean = false,
    provider: MediaProviderType = MediaProviderType.Shuttle
) {
    execSQL(
        "INSERT INTO songs (id, name, track, disc, duration, year, genres, path, albumArtist, artists, album, size, mimeType, lastModified, " +
            "playbackPosition, playCount, lastPlayed, lastCompleted, blacklisted, externalId, mediaProvider) " +
            "VALUES (?, ?, 1, 1, 180000, NULL, '', ?, 'Artist', 'Artist', 'Album', ?, 'audio/mpeg', 1700000000000, 0, ?, ?, NULL, ?, NULL, ?)",
        arrayOf<Any?>(id, "Song $id", path, size, playCount, lastPlayed, if (excluded) 1 else 0, provider.name)
    )
}
