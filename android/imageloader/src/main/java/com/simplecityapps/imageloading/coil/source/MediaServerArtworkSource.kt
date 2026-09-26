package com.simplecityapps.imageloading.coil.source

import com.simplecityapps.imageloading.coil.ArtworkSource
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.settings.ArtworkSettings

/** Album art served by the media server a song streams from. */
internal class MediaServerSongArtworkSource(
    private val artworkSettings: ArtworkSettings,
    private val remoteArtworkProvider: RemoteArtworkProvider
) : ArtworkSource.Remote<Song> {
    override fun handles(model: Song): Boolean = !artworkSettings.localOnly.value

    override suspend fun url(model: Song): String? = remoteArtworkProvider.getAlbumArtworkUrl(model)
}

/** Album art served for the album's first song. */
internal class MediaServerAlbumArtworkSource(
    private val artworkSettings: ArtworkSettings,
    private val songRepository: SongRepository,
    private val remoteArtworkProvider: RemoteArtworkProvider
) : ArtworkSource.Remote<Album> {
    override fun handles(model: Album): Boolean = !artworkSettings.localOnly.value

    override suspend fun url(model: Album): String? = songRepository.firstSongOf(model)?.let { song -> remoteArtworkProvider.getAlbumArtworkUrl(song) }
}

/** Artist art served for the artist's first song. */
internal class MediaServerAlbumArtistArtworkSource(
    private val artworkSettings: ArtworkSettings,
    private val songRepository: SongRepository,
    private val remoteArtworkProvider: RemoteArtworkProvider
) : ArtworkSource.Remote<AlbumArtist> {
    override fun handles(model: AlbumArtist): Boolean = !artworkSettings.localOnly.value

    override suspend fun url(model: AlbumArtist): String? = songRepository.firstSongOf(model)?.let { song -> remoteArtworkProvider.getArtistArtworkUrl(song) }
}
