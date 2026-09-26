package com.simplecityapps.imageloading.coil.source

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.settings.ArtworkSettings
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.settings.defaultSharedPreferences
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class S2ArtworkSourceTest {
    private val artworkSettings = ArtworkSettings(SettingsStore(RuntimeEnvironment.getApplication().defaultSharedPreferences().apply { edit().clear().commit() }))

    @Test
    fun `album art is looked up by encoded album artist and album name`() = runBlocking<Unit> {
        S2AlbumArtworkSource(artworkSettings).url(createAlbum(name = "Rock & Roll", albumArtist = "AC/DC")) shouldBe
            "https://api.shuttlemusicplayer.app/v1/artwork?artist=AC%2FDC&album=Rock+%26+Roll"
    }

    @Test
    fun `artist art is looked up by encoded artist name`() = runBlocking<Unit> {
        S2AlbumArtistArtworkSource(artworkSettings).url(createAlbumArtist(name = "Sigur Rós")) shouldBe
            "https://api.shuttlemusicplayer.app/v1/artwork?artist=Sigur+R%C3%B3s"
    }

    @Test
    fun `albums without a name are not looked up`() {
        S2AlbumArtworkSource(artworkSettings).handles(createAlbum(name = null, albumArtist = "Artist")) shouldBe false
    }

    @Test
    fun `nothing is looked up when artwork is local only`() {
        artworkSettings.localOnly.value = true

        S2AlbumArtworkSource(artworkSettings).handles(createAlbum(name = "Album", albumArtist = "Artist")) shouldBe false
        S2AlbumArtistArtworkSource(artworkSettings).handles(createAlbumArtist(name = "Artist")) shouldBe false
    }

    private fun createAlbum(
        name: String?,
        albumArtist: String
    ) = Album(
        name = name,
        albumArtist = albumArtist,
        artists = listOf(albumArtist),
        songCount = 1,
        duration = 180_000,
        year = null,
        playCount = 0,
        lastSongPlayed = null,
        lastSongCompleted = null,
        groupKey = null,
        mediaProviders = listOf(MediaProviderType.Shuttle),
        artworkVersion = null
    )

    private fun createAlbumArtist(name: String) = AlbumArtist(
        name = name,
        artists = listOf(name),
        albumCount = 1,
        songCount = 1,
        playCount = 0,
        groupKey = AlbumArtistGroupKey(name),
        mediaProviders = listOf(MediaProviderType.Shuttle),
        artworkVersion = null
    )
}
