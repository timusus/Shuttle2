package com.simplecityapps.imageloading.coil.source

import com.simplecityapps.imageloading.coil.ArtworkSource
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.serverArtistId
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

/**
 * The artist's own image on the media server (#653): the server's id for them comes from the first of their songs that
 * pins one down ([serverArtistId]), never another artist credited on the same song.
 */
internal class MediaServerAlbumArtistArtworkSource(
    private val artworkSettings: ArtworkSettings,
    private val songRepository: SongRepository,
    private val remoteArtworkProvider: RemoteArtworkProvider
) : ArtworkSource.Remote<AlbumArtist> {
    override fun handles(model: AlbumArtist): Boolean = !artworkSettings.localOnly.value

    override suspend fun url(model: AlbumArtist): String? = songRepository.songsOf(model)
        .firstNotNullOfOrNull { song -> song.serverArtistId(model.groupKey)?.let { id -> song to id } }
        ?.let { (song, id) -> remoteArtworkProvider.getArtistArtworkUrl(song, id) }
}
