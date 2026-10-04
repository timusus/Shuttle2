package com.simplecityapps.shuttle.shared.artwork

import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.mediaprovider.S2ArtworkApi
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.ArtistHeroArtwork
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.settings.ArtworkSettings
import com.simplecityapps.shuttle.shared.percentEncodedPath
import dev.zacsweers.metro.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.firstOrNull

/**
 * One place an item's artwork may be, and how to ask for it. [authorization] is an `Authorization` header value, so urls
 * stay free of credentials; [headers] are any others it needs, such as a Plex server's `X-Plex-Token`; [unmeteredOnly] means the request must
 * not go over a metered (cellular) network.
 */
data class ArtworkRequest(
    val url: String,
    val authorization: String? = null,
    val unmeteredOnly: Boolean = false,
    val headers: Map<String, String> = emptyMap()
)

/**
 * Where to look for a song's, album's or album artist's artwork (docs/architecture/ios-port/phase-5-ios-app.md, "Artwork"):
 * the requests come from shared Kotlin, pixels stay in Swift's `ArtworkLoader`, which tries them in order until one
 * yields an image.
 *
 * The order is Android's remote chain (`:android:imageloader`'s `CoilModule`): the media server's image, then the S2
 * artwork API by name. An album or artist has no artwork url of its own on the server, so it stands in one of its songs,
 * the same way the `MediaServerArtworkSource`s do. The S2 fallback is what covers an album the server has no image for,
 * or one whose image it fails to serve (Jellyfin answers 500 for some tagged album images). A song from this device
 * leads with its own file's picture.
 */
@Inject
class ArtworkUrls(
    private val artworkSettings: ArtworkSettings,
    private val remoteArtworkProvider: RemoteArtworkProvider,
    private val songRepository: SongRepository
) {
    suspend fun requests(song: Song): List<ArtworkRequest> = listOfNotNull(localRequest(song)) + remoteRequests(song)

    suspend fun requests(album: Album): List<ArtworkRequest> {
        val song = firstSongOf(album)
        return listOfNotNull(song?.let(::localRequest)) + remoteRequests(album, song)
    }

    suspend fun requests(albumArtist: AlbumArtist): List<ArtworkRequest> {
        if (artworkSettings.localOnly.value) return emptyList()
        return listOfNotNull(
            firstSongOf(albumArtist)?.let { song -> serverRequest { remoteArtworkProvider.getArtistArtworkUrl(song) } },
            (albumArtist.name ?: albumArtist.friendlyArtistName)?.let { artist -> s2Request(S2ArtworkApi.artistArtworkUrl(artist)) }
        )
    }

    /**
     * An artist page's hero (#781), the same chain as Android's: the media server's artist image, the S2 API's only when
     * [ArtistHeroArtwork.onlineLookup], then the fallback album's cover.
     */
    suspend fun requests(hero: ArtistHeroArtwork): List<ArtworkRequest> {
        val artist = if (artworkSettings.localOnly.value) {
            emptyList()
        } else {
            listOfNotNull(
                firstSongOf(hero.artist)?.let { song -> serverRequest { remoteArtworkProvider.getArtistArtworkUrl(song) } },
                (hero.artist.name ?: hero.artist.friendlyArtistName)?.takeIf { hero.onlineLookup }?.let { artist -> s2Request(S2ArtworkApi.artistArtworkUrl(artist)) },
            )
        }
        return artist + hero.fallbackAlbum?.let { requests(it) }.orEmpty()
    }

    private suspend fun remoteRequests(song: Song): List<ArtworkRequest> {
        if (artworkSettings.localOnly.value) return emptyList()
        return listOfNotNull(
            serverRequest { remoteArtworkProvider.getAlbumArtworkUrl(song) },
            s2AlbumRequest(artist = song.albumArtist ?: song.friendlyArtistName, album = song.album)
        )
    }

    private suspend fun remoteRequests(
        album: Album,
        firstSong: Song?
    ): List<ArtworkRequest> {
        if (artworkSettings.localOnly.value) return emptyList()
        return listOfNotNull(
            firstSong?.let { song -> serverRequest { remoteArtworkProvider.getAlbumArtworkUrl(song) } },
            s2AlbumRequest(artist = album.albumArtist ?: album.friendlyArtistName, album = album.name)
        )
    }

    /**
     * A song from this device's library (`s2local://...`, #590): its picture, embedded or beside it in its folder, which
     * Swift's `ArtworkLoader` reads from the file. Asked even when artwork is local-only, which is what that setting keeps.
     */
    private fun localRequest(song: Song): ArtworkRequest? {
        if (!song.path.startsWith(LOCAL_PREFIX)) return null
        return ArtworkRequest(LOCAL_PREFIX + song.path.removePrefix(LOCAL_PREFIX).percentEncodedPath())
    }

    /** The server's url, or none when the lookup fails; as on Android, a failing source falls through to the next. */
    private suspend fun serverRequest(url: suspend () -> String?): ArtworkRequest? = try {
        url()?.let { ArtworkRequest(it, headers = remoteArtworkProvider.requestHeaders(it)) }
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

    /** One of their own albums' songs, else one crediting them elsewhere (a credited-only artist's). */
    private suspend fun firstSongOf(albumArtist: AlbumArtist): Song? = songRepository.getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(albumArtist.groupKey))))
        .firstOrNull()
        .orEmpty()
        .let { songs -> songs.firstOrNull { song -> song.albumArtistGroupKey == albumArtist.groupKey } ?: songs.firstOrNull() }

    private companion object {
        const val LOCAL_PREFIX = "s2local://"
    }
}
