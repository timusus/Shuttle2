package com.simplecityapps.imageloading.coil

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.ArtistImageArtwork
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.settings.NowPlayingImage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Instant
import org.junit.Test

class ArtworkKeysTest {
    @Test
    fun `song key is stable when the artwork version is unchanged`() {
        // A rescan can move lastModified without the artwork changing; only the version counts
        val first = createSong(artworkVersion = "v1", lastModified = Instant.fromEpochMilliseconds(1_000))
        val second = createSong(artworkVersion = "v1", lastModified = Instant.fromEpochMilliseconds(2_000))

        first.artworkCacheKey() shouldBe second.artworkCacheKey()
    }

    @Test
    fun `song key changes when the artwork version changes`() {
        createSong(artworkVersion = "v1").artworkCacheKey() shouldNotBe
            createSong(artworkVersion = "v2").artworkCacheKey()
    }

    @Test
    fun `song key keeps its unversioned form without an artwork version`() {
        createSong(artworkVersion = null).artworkCacheKey() shouldBe "song:Artist_Album_Song"
        createSong(artworkVersion = "v1").artworkCacheKey() shouldBe "song:Artist_Album_Song_v1"
    }

    @Test
    fun `album key is stable when the artwork version is unchanged`() {
        createAlbum(artworkVersion = "v1").artworkCacheKey() shouldBe
            createAlbum(artworkVersion = "v1").artworkCacheKey()
    }

    @Test
    fun `album key changes when the artwork version changes`() {
        createAlbum(artworkVersion = "v1").artworkCacheKey() shouldNotBe
            createAlbum(artworkVersion = "v2").artworkCacheKey()
    }

    @Test
    fun `album key keeps its unversioned form without an artwork version`() {
        createAlbum(artworkVersion = null).artworkCacheKey() shouldBe "album:Artist_Album"
    }

    @Test
    fun `album artist key is stable when the artwork version is unchanged`() {
        createAlbumArtist(artworkVersion = "v1").artworkCacheKey() shouldBe
            createAlbumArtist(artworkVersion = "v1").artworkCacheKey()
    }

    @Test
    fun `album artist key changes when the artwork version changes`() {
        createAlbumArtist(artworkVersion = "v1").artworkCacheKey() shouldNotBe
            createAlbumArtist(artworkVersion = "v2").artworkCacheKey()
    }

    @Test
    fun `album artist key keeps its unversioned form without an artwork version`() {
        createAlbumArtist(artworkVersion = null).artworkCacheKey() shouldBe "artist:Artist"
    }

    @Test
    fun `a song never shares a key with an album whose names line up with it`() {
        // Unprefixed, the album "Artist" by "Album_Song"... and the song Artist/Album/Song both came out as "Artist_Album_Song"
        val album = createAlbum(artworkVersion = null).copy(albumArtist = "Artist", name = "Album_Song")

        createSong(artworkVersion = null).artworkCacheKey() shouldNotBe album.artworkCacheKey()
    }

    @Test
    fun `album art now playing artwork is the song itself, keyed as the song`() {
        val song = createSong(artworkVersion = "v1")

        val artwork = nowPlayingArtwork(song, NowPlayingImage.AlbumArt)

        artwork.model shouldBe song
        artwork.cacheKey shouldBe song.artworkCacheKey()
    }

    @Test
    fun `artist image now playing artwork is the album artist's image, falling back to the song, keyed by both`() {
        val first = createSong(artworkVersion = "v1")
        val second = createSong(artworkVersion = "v2").copy(name = "Other", album = "Other Album")

        val firstArtwork = nowPlayingArtwork(first, NowPlayingImage.ArtistImage)
        val secondArtwork = nowPlayingArtwork(second, NowPlayingImage.ArtistImage)

        (firstArtwork.model as ArtistImageArtwork).artist.name shouldBe "Artist"
        (firstArtwork.model as ArtistImageArtwork).song shouldBe first
        // A song that falls back to its own art can't share an entry with another song's
        firstArtwork.cacheKey shouldNotBe secondArtwork.cacheKey
        firstArtwork.cacheKey shouldNotBe nowPlayingArtwork(first, NowPlayingImage.AlbumArt).cacheKey
    }

    @Test
    fun `artist image now playing artwork keeps a song with no album artist as itself`() {
        val song = createSong(artworkVersion = null).copy(albumArtist = null)

        val artwork = nowPlayingArtwork(song, NowPlayingImage.ArtistImage)

        artwork.model shouldBe song
        artwork.cacheKey shouldBe song.artworkCacheKey()
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
