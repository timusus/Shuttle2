package com.simplecityapps.imageloading.coil.source

import android.content.res.AssetFileDescriptor
import android.os.ParcelFileDescriptor
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** [MediaStoreAlbumArtworkSource]'s choice of which song's thumbnail to fetch. */
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
    fun `nothing is fetched when no song has a MediaStore id`() = runBlocking<Unit> {
        val songs = listOf(createSong(MediaProviderType.Jellyfin, externalId = "remote"))

        MediaStoreAlbumArtworkSource(context, FakeSongRepository(songs)).open(createAlbum()) shouldBe null
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
}
