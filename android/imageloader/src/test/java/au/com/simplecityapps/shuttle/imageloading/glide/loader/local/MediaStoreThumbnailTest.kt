package au.com.simplecityapps.shuttle.imageloading.glide.loader.local

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import org.junit.Test

class MediaStoreThumbnailTest {
    @Test
    fun `directory loaders list folder images for every provider where shared storage listings include them`() {
        canListFolderImages(listOf(MediaProviderType.MediaStore), sharedStorageListsImages = true) shouldBe true
        canListFolderImages(listOf(MediaProviderType.Shuttle), sharedStorageListsImages = true) shouldBe true
    }

    @Test
    fun `directory loaders skip MediaStore-only models where shared storage listings leave images out`() {
        canListFolderImages(listOf(MediaProviderType.MediaStore), sharedStorageListsImages = false) shouldBe false
    }

    @Test
    fun `directory loaders still read SAF folders where shared storage listings leave images out`() {
        // The SAF tree grant covers every file in the tree
        canListFolderImages(listOf(MediaProviderType.Shuttle), sharedStorageListsImages = false) shouldBe true
        canListFolderImages(listOf(MediaProviderType.MediaStore, MediaProviderType.Shuttle), sharedStorageListsImages = false) shouldBe true
    }

    @Test
    fun `a MediaStore song's thumbnail id is its MediaStore audio id`() {
        createSong(MediaProviderType.MediaStore, externalId = "42").mediaStoreId() shouldBe 42L
    }

    @Test
    fun `songs from other providers have no MediaStore thumbnail`() {
        createSong(MediaProviderType.Shuttle, externalId = null).mediaStoreId() shouldBe null
        createSong(MediaProviderType.Jellyfin, externalId = "a1b2c3").mediaStoreId() shouldBe null
        createSong(MediaProviderType.MediaStore, externalId = null).mediaStoreId() shouldBe null
    }

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
