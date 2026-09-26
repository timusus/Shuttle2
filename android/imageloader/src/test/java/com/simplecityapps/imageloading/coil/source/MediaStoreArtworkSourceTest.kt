package com.simplecityapps.imageloading.coil.source

import android.content.res.AssetFileDescriptor
import android.os.ParcelFileDescriptor
import com.simplecityapps.mediaprovider.SongPathRemap
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** [MediaStoreAlbumArtworkSource] and [MediaStoreAlbumArtistArtworkSource]'s choice of which song's thumbnail to fetch. */
@RunWith(RobolectricTestRunner::class)
class MediaStoreArtworkSourceTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        FakeMediaAudioProvider.register()
        val file = tempFolder.newFile().apply { writeBytes("art".toByteArray()) }
        FakeMediaAudioProvider.behavior = { AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), 0, file.length()) }
    }

    @Test
    fun `album art comes from the first song with a MediaStore id, skipping songs from other providers`() = runBlocking<Unit> {
        val songs =
            listOf(
                createSong(MediaProviderType.Jellyfin, externalId = "remote"),
                createSong(MediaProviderType.MediaStore, externalId = "7"),
                createSong(MediaProviderType.MediaStore, externalId = "8")
            )
        val source = MediaStoreAlbumArtworkSource(context, FakeSongRepository(songs))

        source.open(createAlbum()) shouldNotBe null
        FakeMediaAudioProvider.requestedIds shouldBe listOf(7L)
    }

    @Test
    fun `album artist art comes from the artist's first song with a MediaStore id`() = runBlocking<Unit> {
        val songs =
            listOf(
                createSong(MediaProviderType.Jellyfin, externalId = "remote"),
                createSong(MediaProviderType.MediaStore, externalId = "12")
            )
        val source = MediaStoreAlbumArtistArtworkSource(context, FakeSongRepository(songs))

        source.open(createAlbumArtist()) shouldNotBe null
        FakeMediaAudioProvider.requestedIds shouldBe listOf(12L)
    }

    @Test
    fun `nothing is fetched when no song has a MediaStore id`() = runBlocking<Unit> {
        val songs = listOf(createSong(MediaProviderType.Jellyfin, externalId = "remote"))

        MediaStoreAlbumArtworkSource(context, FakeSongRepository(songs)).open(createAlbum()) shouldBe null
        MediaStoreAlbumArtistArtworkSource(context, FakeSongRepository(songs)).open(createAlbumArtist()) shouldBe null
        FakeMediaAudioProvider.requestedIds shouldBe emptyList()
    }

    private fun createAlbum() = Album(
        name = "Album",
        albumArtist = "Artist",
        artists = listOf("Artist"),
        songCount = 1,
        duration = 180_000,
        year = null,
        playCount = 0,
        lastSongPlayed = null,
        lastSongCompleted = null,
        groupKey = null,
        mediaProviders = listOf(MediaProviderType.MediaStore),
        artworkVersion = null
    )

    private fun createAlbumArtist() = AlbumArtist(
        name = "Artist",
        artists = listOf("Artist"),
        albumCount = 1,
        songCount = 1,
        playCount = 0,
        groupKey = AlbumArtistGroupKey("Artist"),
        mediaProviders = listOf(MediaProviderType.MediaStore),
        artworkVersion = null
    )

    private fun createSong(
        mediaProvider: MediaProviderType,
        externalId: String?
    ) = Song(
        id = 0,
        name = "Song",
        albumArtist = "Artist",
        artists = listOf("Artist"),
        album = "Album",
        track = 1,
        disc = 1,
        duration = 180_000,
        date = null,
        genres = emptyList(),
        path = "/music/song.mp3",
        size = 0,
        mimeType = "audio/mpeg",
        lastModified = null,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        externalId = externalId,
        mediaProvider = mediaProvider,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )

    private class FakeSongRepository(private val songs: List<Song>) : SongRepository {
        override fun getSongs(query: SongQuery): Flow<List<Song>?> = flowOf(songs)

        override suspend fun insert(
            songs: List<Song>,
            mediaProviderType: MediaProviderType
        ) {}

        override suspend fun update(song: Song): Int = 0

        override suspend fun update(songs: List<Song>) {}

        override suspend fun remove(song: Song) {}

        override suspend fun removeAll(mediaProviderType: MediaProviderType) {}

        override suspend fun insertUpdateAndDelete(
            inserts: List<Song>,
            updates: List<Song>,
            deletes: List<Song>,
            mediaProviderType: MediaProviderType
        ): Triple<Int, Int, Int> = Triple(0, 0, 0)

        override suspend fun remapPaths(
            remaps: List<SongPathRemap>,
            mediaProviderType: MediaProviderType
        ): List<SongPathRemap> = remaps

        override suspend fun incrementPlayCount(song: Song) {}

        override suspend fun setPlaybackPosition(
            song: Song,
            playbackPosition: Int
        ) {}

        override suspend fun setExcluded(
            songs: List<Song>,
            excluded: Boolean
        ) {}

        override suspend fun clearExcludeList() {}
    }
}
