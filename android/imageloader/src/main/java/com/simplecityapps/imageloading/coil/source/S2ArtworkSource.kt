package com.simplecityapps.imageloading.coil.source

import com.simplecityapps.imageloading.coil.ArtworkSource
import com.simplecityapps.mediaprovider.S2ArtworkApi
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.settings.ArtworkSettings

/** Album art looked up by artist and album name on the S2 artwork API. */
internal class S2SongArtworkSource(
    private val artworkSettings: ArtworkSettings
) : ArtworkSource.Remote<Song> {
    override fun handles(model: Song): Boolean = !artworkSettings.localOnly.value && model.album != null && (model.albumArtist ?: model.friendlyArtistName) != null

    override suspend fun url(model: Song): String? {
        val artist = model.albumArtist ?: model.friendlyArtistName ?: return null
        val album = model.album ?: return null
        return S2ArtworkApi.albumArtworkUrl(artist, album)
    }
}

internal class S2AlbumArtworkSource(
    private val artworkSettings: ArtworkSettings
) : ArtworkSource.Remote<Album> {
    override fun handles(model: Album): Boolean = !artworkSettings.localOnly.value && model.name != null && (model.albumArtist ?: model.friendlyArtistName) != null

    override suspend fun url(model: Album): String? {
        val artist = model.albumArtist ?: model.friendlyArtistName ?: return null
        val album = model.name ?: return null
        return S2ArtworkApi.albumArtworkUrl(artist, album)
    }
}

/** Artist art looked up by name on the S2 artwork API. */
internal class S2AlbumArtistArtworkSource(
    private val artworkSettings: ArtworkSettings
) : ArtworkSource.Remote<AlbumArtist> {
    override fun handles(model: AlbumArtist): Boolean = !artworkSettings.localOnly.value && (model.name ?: model.friendlyArtistName) != null

    override suspend fun url(model: AlbumArtist): String? {
        val artist = model.name ?: model.friendlyArtistName ?: return null
        return S2ArtworkApi.artistArtworkUrl(artist)
    }
}
