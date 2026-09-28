package com.simplecityapps.shuttle.shared.artwork

import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.mediaprovider.S2ArtworkApi
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.settings.ArtworkSettings
import dev.zacsweers.metro.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.firstOrNull

/**
 * One place an item's artwork may be, and how to ask for it. [authorization] is an `Authorization` header value, so urls
 * stay free of credentials; [unmeteredOnly] means the request must not go over a metered (cellular) network.
 */
data class ArtworkRequest(
    val url: String,
    val authorization: String? = null,
    val unmeteredOnly: Boolean = false
)

/**
 * Where to look for a song's, album's or album artist's artwork (docs/architecture/ios-port/phase-5-ios-app.md, "Artwork"):
 * the requests come from shared Kotlin, pixels stay in Swift's `ArtworkLoader`, which tries them in order until one
 * yields an image.
 *
 * The order is Android's remote chain (`:android:imageloader`'s `CoilModule`): the media server's image, then the S2
 * artwork API by name. An album or artist has no artwork url of its own on the server, so it stands in one of its songs,
 * the same way the `MediaServerArtworkSource`s do. The S2 fallback is what covers an album the server has no image for,
 * or one whose image it fails to serve (Jellyfin answers 500 for some tagged album images).
 */
@Inject
class ArtworkUrls(
    private val artworkSettings: ArtworkSettings,
    private val remoteArtworkProvider: RemoteArtworkProvider,
    private val songRepository: SongRepository
) {
    suspend fun requests(song: Song): List<ArtworkRequest> {
        if (artworkSettings.localOnly.value) return emptyList()
        return listOfNotNull(
            serverRequest { remoteArtworkProvider.getAlbumArtworkUrl(song) },
            s2AlbumRequest(artist = song.albumArtist ?: song.friendlyArtistName, album = song.album)
        )
    }

    suspend fun requests(album: Album): List<ArtworkRequest> {
        if (artworkSettings.localOnly.value) return emptyList()
        return listOfNotNull(
            firstSongOf(album)?.let { song -> serverRequest { remoteArtworkProvider.getAlbumArtworkUrl(song) } },
            s2AlbumRequest(artist = album.albumArtist ?: album.friendlyArtistName, album = album.name)
        )
    }

    suspend fun requests(albumArtist: AlbumArtist): List<ArtworkRequest> {
        if (artworkSettings.localOnly.value) return emptyList()
        return listOfNotNull(
            firstSongOf(albumArtist)?.let { song -> serverRequest { remoteArtworkProvider.getArtistArtworkUrl(song) } },
            (albumArtist.name ?: albumArtist.friendlyArtistName)?.let { artist -> s2Request(S2ArtworkApi.artistArtworkUrl(artist)) }
        )
    }

    /** The server's url, or none when the lookup fails; as on Android, a failing source falls through to the next. */
    private suspend fun serverRequest(url: suspend () -> String?): ArtworkRequest? = try {
        url()?.let(::ArtworkRequest)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun s2AlbumRequest(
        artist: String?,
        album: String?
    ): ArtworkRequest? {
        if (artist == null || album == null) return null
        return s2Request(S2ArtworkApi.albumArtworkUrl(artist, album))
    }

    /** The S2 API honours the wifi-only setting, as Android's does; media server artwork doesn't, being the server already streamed from. */
    private fun s2Request(url: String) = ArtworkRequest(url, authorization = S2ArtworkApi.authorization, unmeteredOnly = artworkSettings.wifiOnly.value)

    private suspend fun firstSongOf(album: Album): Song? = songRepository.getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(album.groupKey))))
        .firstOrNull()
        .orEmpty()
        .firstOrNull()

    private suspend fun firstSongOf(albumArtist: AlbumArtist): Song? = songRepository.getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(albumArtist.groupKey))))
        .firstOrNull()
        .orEmpty()
        .firstOrNull()
}
