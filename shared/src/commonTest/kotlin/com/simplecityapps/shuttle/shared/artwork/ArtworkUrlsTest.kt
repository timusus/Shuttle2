package com.simplecityapps.shuttle.shared.artwork

import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.mediaprovider.S2ArtworkApi
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.ArtistHeroArtwork
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.settings.ArtworkSettings
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.ui.actions.LoadArtistArtwork
import com.simplecityapps.shuttle.ui.actions.ObserveArtistAlbums
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/**
 * Artwork requests try the [RemoteArtworkProvider] (standing an album or artist in with one of its songs), then the S2
 * artwork API by name: Android's remote chain. An artist's follow the shared [ArtistHeroArtwork] rule.
 */
class ArtworkUrlsTest {
    private val songRepository = FakeSongRepository().apply { applyQueryPredicates = true }
    private val remoteArtworkProvider = FakeRemoteArtworkProvider()
    private val artworkSettings = ArtworkSettings(SettingsStore(InMemoryKeyValueStore()))

    private val albumRepository = FakeAlbumRepository().apply { applyQueryPredicates = true }

    private val artworkUrls = ArtworkUrls(artworkSettings, remoteArtworkProvider, songRepository, LoadArtistArtwork(ObserveArtistAlbums(albumRepository, songRepository), songRepository))

    /** A library of [songs] and their albums, the way the real repositories derive them. */
    private fun library(vararg songs: Song) {
        songRepository.setSongs(songs.toList())
        albumRepository.setAlbums(songs.distinctBy { it.albumGroupKey }.map(::album))
    }

    @Test
    fun `the server's request carries the headers its provider asks for - the S2 API's does not`() = runTest {
        remoteArtworkProvider.headers = mapOf("X-Token" to "secret")

        artworkUrls.requests(song("song-1")) shouldBe listOf(
            ArtworkRequest("https://example.com/song-1/album", headers = mapOf("X-Token" to "secret")),
            s2("$S2_URL?artist=The+Artist&album=Album+%26+Co")
        )
    }

    @Test
    fun `song artwork is the remote provider's album artwork and then the S2 API's by album artist and album`() = runTest {
        val song = song("song-1")

        artworkUrls.requests(song) shouldBe listOf(ArtworkRequest("https://example.com/song-1/album"), s2("$S2_URL?artist=The+Artist&album=Album+%26+Co"))
        remoteArtworkProvider.albumArtworkRequests shouldBe listOf(song)
    }

    @Test
    fun `album artwork is the album's first song's album artwork and then the S2 API's`() = runTest {
        val song = song("song-1")
        songRepository.setSongs(listOf(song))

        artworkUrls.requests(album(song)) shouldBe listOf(ArtworkRequest("https://example.com/song-1/album"), s2("$S2_URL?artist=The+Artist&album=Album+%26+Co"))
        remoteArtworkProvider.albumArtworkRequests shouldBe listOf(song)
    }

    @Test
    fun `album artist artwork is the hero's - the server's artist image at a minimum size, then their top album's cover`() = runTest {
        val song = song("song-1")
        library(song)

        artworkUrls.requests(albumArtist(song)) shouldBe listOf(
            ArtworkRequest("https://example.com/song-1/artist", minimumSize = ArtistHeroArtwork.MIN_ARTIST_IMAGE_SIZE),
            ArtworkRequest("https://example.com/song-1/album"),
            s2("$S2_URL?artist=The+Artist&album=Album+%26+Co"),
        )
        remoteArtworkProvider.artistArtworkRequests shouldBe listOf(song)
    }

    @Test
    fun `the S2 API's artist image is asked for only when their tags carry one MusicBrainz id`() = runTest {
        val song = song("song-1").copy(mbAlbumArtistIds = listOf("a74b1b7f-71a5-4011-9441-d0b5e4122711"))
        library(song)
        remoteArtworkProvider.hasArtwork = false

        artworkUrls.requests(albumArtist(song)) shouldBe listOf(
            s2("$S2_URL?artist=The+Artist").copy(minimumSize = ArtistHeroArtwork.MIN_ARTIST_IMAGE_SIZE),
            s2("$S2_URL?artist=The+Artist&album=Album+%26+Co"),
        )
    }

    @Test
    fun `an item the server has no artwork for still has the S2 API's`() = runTest {
        val song = song("song-1")
        library(song)
        remoteArtworkProvider.hasArtwork = false

        artworkUrls.requests(song) shouldBe listOf(s2("$S2_URL?artist=The+Artist&album=Album+%26+Co"))
        artworkUrls.requests(album(song)) shouldBe listOf(s2("$S2_URL?artist=The+Artist&album=Album+%26+Co"))
        artworkUrls.requests(albumArtist(song)) shouldBe listOf(s2("$S2_URL?artist=The+Artist&album=Album+%26+Co"))
    }

    @Test
    fun `a failing server lookup falls through to the S2 API`() = runTest {
        val song = song("song-1")
        remoteArtworkProvider.failure = IllegalStateException("server unreachable")

        artworkUrls.requests(song) shouldBe listOf(s2("$S2_URL?artist=The+Artist&album=Album+%26+Co"))
    }

    @Test
    fun `an album with no songs in the library has only the S2 API's artwork`() = runTest {
        val song = song("song-1")

        artworkUrls.requests(album(song)) shouldBe listOf(s2("$S2_URL?artist=The+Artist&album=Album+%26+Co"))
        remoteArtworkProvider.albumArtworkRequests shouldBe emptyList()
    }

    @Test
    fun `an album artist with nothing in the library has no artwork`() = runTest {
        val song = song("song-1")
        library()

        artworkUrls.requests(albumArtist(song)) shouldBe emptyList()
        remoteArtworkProvider.artistArtworkRequests shouldBe emptyList()
    }

    @Test
    fun `a song with no album is not looked up on the S2 API`() = runTest {
        val song = song("song-1").copy(album = null)

        artworkUrls.requests(song) shouldBe listOf(ArtworkRequest("https://example.com/song-1/album"))
    }

    @Test
    fun `S2 API requests stay off metered networks while artwork is wifi-only as it is by default`() = runTest {
        artworkUrls.requests(song("song-1")).map { it.unmeteredOnly } shouldBe listOf(false, true)

        artworkSettings.wifiOnly.value = false

        artworkUrls.requests(song("song-1")).map { it.unmeteredOnly } shouldBe listOf(false, false)
    }

    @Test
    fun `no artwork of any kind when artwork is local-only`() = runTest {
        val song = song("song-1")
        library(song)
        artworkSettings.localOnly.value = true

        artworkUrls.requests(song) shouldBe emptyList()
        artworkUrls.requests(album(song)) shouldBe emptyList()
        artworkUrls.requests(albumArtist(song)) shouldBe emptyList()
        remoteArtworkProvider.albumArtworkRequests shouldBe emptyList()
        remoteArtworkProvider.artistArtworkRequests shouldBe emptyList()
    }

    @Test
    fun `a local song's artwork is its file's first - even when artwork is local-only`() = runTest {
        val song = song("song-1").copy(path = "s2local://documents/Björk/03 Hyperballad.flac", mediaProvider = MediaProviderType.Shuttle, externalId = null)
        library(song)
        val local = ArtworkRequest("s2local://documents/Bj%C3%B6rk/03%20Hyperballad.flac")
        remoteArtworkProvider.hasArtwork = false

        artworkUrls.requests(song) shouldBe listOf(local, s2("$S2_URL?artist=The+Artist&album=Album+%26+Co"))
        artworkUrls.requests(album(song)) shouldBe listOf(local, s2("$S2_URL?artist=The+Artist&album=Album+%26+Co"))
        artworkUrls.requests(albumArtist(song)) shouldBe listOf(local, s2("$S2_URL?artist=The+Artist&album=Album+%26+Co"))

        artworkSettings.localOnly.value = true

        artworkUrls.requests(song) shouldBe listOf(local)
        artworkUrls.requests(album(song)) shouldBe listOf(local)
        artworkUrls.requests(albumArtist(song)) shouldBe listOf(local)
    }

    private fun s2(url: String) = ArtworkRequest(url, authorization = S2ArtworkApi.authorization, unmeteredOnly = true)

    private fun song(externalId: String) = Song(
        id = 0,
        name = "Song",
        albumArtist = "The Artist",
        artists = listOf("The Artist"),
        album = "Album & Co",
        track = null,
        disc = null,
        duration = 180_000,
        date = null,
        genres = emptyList(),
        path = "jellyfin://item/$externalId",
        size = 0,
        mimeType = "Audio/*",
        lastModified = null,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        externalId = externalId,
        mediaProvider = MediaProviderType.Jellyfin,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )

    /** An album whose group key matches [song], the way the real repository derives it from an imported song. */
    private fun album(song: Song) = Album(
        name = song.album,
        albumArtist = song.albumArtist,
        artists = song.artists,
        songCount = 1,
        duration = song.duration,
        year = null,
        playCount = 0,
        lastSongPlayed = null,
        lastSongCompleted = null,
        groupKey = song.albumGroupKey,
        mediaProviders = listOf(song.mediaProvider)
    )

    private fun albumArtist(song: Song) = AlbumArtist(
        name = song.albumArtist,
        artists = song.artists,
        albumCount = 1,
        songCount = 1,
        playCount = 0,
        groupKey = song.albumArtistGroupKey,
        mediaProviders = listOf(song.mediaProvider)
    )

    private class FakeRemoteArtworkProvider : RemoteArtworkProvider {
        val albumArtworkRequests = mutableListOf<Song>()
        val artistArtworkRequests = mutableListOf<Song>()
        var hasArtwork = true
        var failure: Exception? = null

        var headers = emptyMap<String, String>()

        override fun requestHeaders(url: String): Map<String, String> = if (url.startsWith("https://example.com/")) headers else emptyMap()

        override fun handles(scheme: String?): Boolean = scheme == "jellyfin"

        override suspend fun getAlbumArtworkUrl(song: Song): String? {
            albumArtworkRequests += song
            failure?.let { throw it }
            return "https://example.com/${song.externalId}/album".takeIf { hasArtwork }
        }

        override suspend fun getArtistArtworkUrl(song: Song): String? {
            artistArtworkRequests += song
            failure?.let { throw it }
            return "https://example.com/${song.externalId}/artist".takeIf { hasArtwork }
        }
    }

    private companion object {
        const val S2_URL = "https://api.shuttlemusicplayer.app/v1/artwork"
    }
}
