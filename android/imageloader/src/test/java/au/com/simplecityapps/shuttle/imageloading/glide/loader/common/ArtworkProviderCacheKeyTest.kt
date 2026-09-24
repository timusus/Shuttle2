package au.com.simplecityapps.shuttle.imageloading.glide.loader.common

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Instant
import org.junit.Test

class ArtworkProviderCacheKeyTest {
    @Test
    fun `song key is stable when the artwork version is unchanged`() {
        // A rescan can move lastModified without the artwork changing; only the version counts
        val first = createSong(artworkVersion = "v1", lastModified = Instant.fromEpochMilliseconds(1_000))
        val second = createSong(artworkVersion = "v1", lastModified = Instant.fromEpochMilliseconds(2_000))

        SongArtworkProvider(first).getCacheKey() shouldBe SongArtworkProvider(second).getCacheKey()
    }

    @Test
    fun `song key changes when the artwork version changes`() {
        SongArtworkProvider(createSong(artworkVersion = "v1")).getCacheKey() shouldNotBe
            SongArtworkProvider(createSong(artworkVersion = "v2")).getCacheKey()
    }

    @Test
    fun `song key keeps its unversioned form without an artwork version`() {
        SongArtworkProvider(createSong(artworkVersion = null)).getCacheKey() shouldBe "Artist_Album_Song"
        SongArtworkProvider(createSong(artworkVersion = "v1")).getCacheKey() shouldBe "Artist_Album_Song_v1"
    }

    @Test
    fun `album key is stable when the artwork version is unchanged`() {
        AlbumArtworkProvider(createAlbum(artworkVersion = "v1")).getCacheKey() shouldBe
            AlbumArtworkProvider(createAlbum(artworkVersion = "v1")).getCacheKey()
    }

    @Test
    fun `album key changes when the artwork version changes`() {
        AlbumArtworkProvider(createAlbum(artworkVersion = "v1")).getCacheKey() shouldNotBe
            AlbumArtworkProvider(createAlbum(artworkVersion = "v2")).getCacheKey()
    }

    @Test
    fun `album key keeps its unversioned form without an artwork version`() {
        AlbumArtworkProvider(createAlbum(artworkVersion = null)).getCacheKey() shouldBe "Artist_Album"
    }

    @Test
    fun `album artist key is stable when the artwork version is unchanged`() {
        AlbumArtistArtworkProvider(createAlbumArtist(artworkVersion = "v1")).getCacheKey() shouldBe
            AlbumArtistArtworkProvider(createAlbumArtist(artworkVersion = "v1")).getCacheKey()
    }

    @Test
    fun `album artist key changes when the artwork version changes`() {
        AlbumArtistArtworkProvider(createAlbumArtist(artworkVersion = "v1")).getCacheKey() shouldNotBe
            AlbumArtistArtworkProvider(createAlbumArtist(artworkVersion = "v2")).getCacheKey()
    }

    @Test
    fun `album artist key keeps its unversioned form without an artwork version`() {
        AlbumArtistArtworkProvider(createAlbumArtist(artworkVersion = null)).getCacheKey() shouldBe "Artist"
    }

    private fun createSong(
        artworkVersion: String?,
        lastModified: Instant? = null
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
        lastModified = lastModified,
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
        channelCount = null,
        artworkVersion = artworkVersion
    )

    private fun createAlbum(artworkVersion: String?) = Album(
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
        mediaProviders = listOf(MediaProviderType.Shuttle),
        artworkVersion = artworkVersion
    )

    private fun createAlbumArtist(artworkVersion: String?) = AlbumArtist(
        name = "Artist",
        artists = listOf("Artist"),
        albumCount = 1,
        songCount = 1,
        playCount = 0,
        groupKey = AlbumArtistGroupKey("artist"),
        mediaProviders = listOf(MediaProviderType.Shuttle),
        artworkVersion = artworkVersion
    )
}
