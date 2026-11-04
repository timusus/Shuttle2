package com.simplecityapps.imageloading.coil

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.ArtistHeroArtwork
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ArtistHeroSourcesTest {
    private val artist = AlbumArtist(
        name = "Artist",
        artists = listOf("Artist"),
        albumCount = 1,
        songCount = 1,
        playCount = 0,
        groupKey = AlbumArtistGroupKey("artist"),
        mediaProviders = listOf(MediaProviderType.Shuttle),
    )

    private val album = Album(
        name = "Album",
        albumArtist = "Artist",
        artists = listOf("Artist"),
        songCount = 1,
        duration = 0,
        year = null,
        playCount = 0,
        lastSongPlayed = null,
        lastSongCompleted = null,
        groupKey = null,
        mediaProviders = listOf(MediaProviderType.Shuttle),
    )

    @Test
    fun `an artist image of any size is tried before the album cover and after the same sources at the minimum size`() = runTest {
        val hero = ArtistHeroArtwork(artist, onlineLookup = true, fallbackAlbum = album)

        steps(hero) shouldBe listOf("own@500", "online@500", "own@0", "online@0", "album@0")
    }

    @Test
    fun `the online source is skipped where the hero doesn't allow the lookup`() = runTest {
        val hero = ArtistHeroArtwork(artist, onlineLookup = false, fallbackAlbum = album)

        steps(hero) shouldBe listOf("own@500", "own@0", "album@0")
    }

    private suspend fun steps(hero: ArtistHeroArtwork): List<String> = artistHeroSources(
        ownSources = listOf(labelled<AlbumArtist>("own")),
        onlineSource = labelled("online"),
        albumSources = listOf(labelled<Album>("album")),
    ).filter { it.handles(hero) }
        .map { source -> "${(source as ArtworkSource.Remote).url(hero)}@${source.minimumSize}" }

    private fun <T : Any> labelled(name: String) = object : ArtworkSource.Remote<T> {
        override suspend fun url(model: T): String? = name
    }
}
