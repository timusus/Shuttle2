package com.simplecityapps.imageloading.coil.source

import com.simplecityapps.imageloading.coil.ArtworkSource
import com.simplecityapps.imageloading.di.CoilModule.S2_ARTWORK_HOST
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.settings.ArtworkSettings
import java.net.URLEncoder

/** Album art looked up by artist and album name on the S2 artwork API. */
internal class S2SongArtworkSource(
    private val artworkSettings: ArtworkSettings
) : ArtworkSource.Remote<Song> {
    override fun handles(model: Song): Boolean = !artworkSettings.localOnly.value && model.album != null && (model.albumArtist ?: model.friendlyArtistName) != null

    override suspend fun url(model: Song): String? {
        val artist = model.albumArtist ?: model.friendlyArtistName ?: return null
        val album = model.album ?: return null
        return s2AlbumArtworkUrl(artist, album)
    }
}

internal class S2AlbumArtworkSource(
    private val artworkSettings: ArtworkSettings
) : ArtworkSource.Remote<Album> {
    override fun handles(model: Album): Boolean = !artworkSettings.localOnly.value && model.name != null && (model.albumArtist ?: model.friendlyArtistName) != null

    override suspend fun url(model: Album): String? {
        val artist = model.albumArtist ?: model.friendlyArtistName ?: return null
        val album = model.name ?: return null
        return s2AlbumArtworkUrl(artist, album)
    }
}

/** Artist art looked up by name on the S2 artwork API. */
internal class S2AlbumArtistArtworkSource(
    private val artworkSettings: ArtworkSettings
) : ArtworkSource.Remote<AlbumArtist> {
    override fun handles(model: AlbumArtist): Boolean = !artworkSettings.localOnly.value && (model.name ?: model.friendlyArtistName) != null

    override suspend fun url(model: AlbumArtist): String? {
        val artist = model.name ?: model.friendlyArtistName ?: return null
        return "https://$S2_ARTWORK_HOST/v1/artwork?artist=${artist.urlEncode()}"
    }
}

private fun s2AlbumArtworkUrl(
    artist: String,
    album: String
): String = "https://$S2_ARTWORK_HOST/v1/artwork?artist=${artist.urlEncode()}&album=${album.urlEncode()}"

private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
