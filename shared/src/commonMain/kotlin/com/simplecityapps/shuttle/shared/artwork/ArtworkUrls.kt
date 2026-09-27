package com.simplecityapps.shuttle.shared.artwork

import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.settings.ArtworkSettings
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.firstOrNull

/**
 * Authenticated artwork urls on the signed-in server (docs/architecture/ios-port/phase-5-ios-app.md, "Artwork"):
 * urls come from shared Kotlin, pixels stay in Swift's `ArtworkLoader`. An album or artist has no artwork url of
 * its own on the server, so it stands in one of its songs, the same way `:android:imageloader`'s
 * `MediaServerArtworkSource`s do.
 */
@Inject
class ArtworkUrls(
    private val artworkSettings: ArtworkSettings,
    private val remoteArtworkProvider: RemoteArtworkProvider,
    private val songRepository: SongRepository
) {
    suspend fun url(song: Song): String? {
        if (artworkSettings.localOnly.value) return null
        return remoteArtworkProvider.getAlbumArtworkUrl(song)
    }

    suspend fun url(album: Album): String? {
        if (artworkSettings.localOnly.value) return null
        return firstSongOf(album)?.let { song -> remoteArtworkProvider.getAlbumArtworkUrl(song) }
    }

    suspend fun url(albumArtist: AlbumArtist): String? {
        if (artworkSettings.localOnly.value) return null
        return firstSongOf(albumArtist)?.let { song -> remoteArtworkProvider.getArtistArtworkUrl(song) }
    }

    private suspend fun firstSongOf(album: Album): Song? = songRepository.getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(album.groupKey))))
        .firstOrNull()
        .orEmpty()
        .firstOrNull()

    private suspend fun firstSongOf(albumArtist: AlbumArtist): Song? = songRepository.getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(albumArtist.groupKey))))
        .firstOrNull()
        .orEmpty()
        .firstOrNull()
}
