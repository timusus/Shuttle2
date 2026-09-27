package com.simplecityapps.shuttle.shared.artwork

import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.settings.ArtworkSettings
import com.simplecityapps.shuttle.settings.SettingsStore
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/** Artwork urls delegate to the [RemoteArtworkProvider], standing an album or artist in with one of its songs. */
class ArtworkUrlsTest {
    private val songRepository = FakeSongRepository().apply { applyQueryPredicates = true }
    private val remoteArtworkProvider = FakeRemoteArtworkProvider()
    private val artworkSettings = ArtworkSettings(SettingsStore(InMemoryKeyValueStore()))

    private val artworkUrls = ArtworkUrls(artworkSettings, remoteArtworkProvider, songRepository)

    @Test
    fun `song artwork is the remote provider's album artwork`() = runTest {
        val song = song("song-1")

        artworkUrls.url(song) shouldBe "https://example.com/song-1/album"
        remoteArtworkProvider.albumArtworkRequests shouldBe listOf(song)
    }

    @Test
    fun `album artwork is the album's first song's album artwork`() = runTest {
        val song = song("song-1")
        songRepository.setSongs(listOf(song))

        artworkUrls.url(album(song)) shouldBe "https://example.com/song-1/album"
        remoteArtworkProvider.albumArtworkRequests shouldBe listOf(song)
    }

    @Test
    fun `album artist artwork is the artist's first song's artist artwork`() = runTest {
        val song = song("song-1")
        songRepository.setSongs(listOf(song))

        artworkUrls.url(albumArtist(song)) shouldBe "https://example.com/song-1/artist"
        remoteArtworkProvider.artistArtworkRequests shouldBe listOf(song)
    }

    @Test
    fun `an album with no songs in the library has no artwork`() = runTest {
        val song = song("song-1")

        artworkUrls.url(album(song)) shouldBe null
        remoteArtworkProvider.albumArtworkRequests shouldBe emptyList()
    }

    @Test
    fun `an album artist with no songs in the library has no artwork`() = runTest {
        val song = song("song-1")

        artworkUrls.url(albumArtist(song)) shouldBe null
        remoteArtworkProvider.artistArtworkRequests shouldBe emptyList()
    }

    @Test
    fun `no artwork of any kind when artwork is local-only`() = runTest {
        val song = song("song-1")
        songRepository.setSongs(listOf(song))
        artworkSettings.localOnly.value = true

        artworkUrls.url(song) shouldBe null
        artworkUrls.url(album(song)) shouldBe null
        artworkUrls.url(albumArtist(song)) shouldBe null
        remoteArtworkProvider.albumArtworkRequests shouldBe emptyList()
        remoteArtworkProvider.artistArtworkRequests shouldBe emptyList()
    }

    private fun song(externalId: String) = Song(
        id = 0,
        name = "Song",
        albumArtist = "Artist",
        artists = listOf("Artist"),
        album = "Album",
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

        override fun handles(scheme: String?): Boolean = scheme == "jellyfin"

        override suspend fun getAlbumArtworkUrl(song: Song): String? {
            albumArtworkRequests += song
            return "https://example.com/${song.externalId}/album"
        }

        override suspend fun getArtistArtworkUrl(song: Song): String? {
            artistArtworkRequests += song
            return "https://example.com/${song.externalId}/artist"
        }
    }
}
