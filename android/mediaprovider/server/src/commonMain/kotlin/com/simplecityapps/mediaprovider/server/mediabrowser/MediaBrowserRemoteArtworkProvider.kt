package com.simplecityapps.mediaprovider.server.mediabrowser

import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.shuttle.model.Song
import io.ktor.http.parseUrl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Artwork urls on the signed-in server; the album's image, else the song's own; null when the server doesn't know the song's album, or when signed out. */
open class MediaBrowserRemoteArtworkProvider(
    private val authenticationManager: MediaBrowserAuthenticationManager,
    private val itemsService: ItemsService
) : RemoteArtworkProvider {
    private val scheme = authenticationManager.server.songPathPrefix.substringBefore("://")

    override fun handles(scheme: String?): Boolean = scheme == this.scheme

    override suspend fun getAlbumArtworkUrl(song: Song): String? {
        val address = authenticationManager.getAddress() ?: return null
        val credentials = authenticationManager.getAuthenticatedCredentials() ?: return null

        val sibling = song.artworkItemId
        if (sibling != null) {
            // Emby sends no album image tag, so a stored version can't say whether the album has an image
            val albumId = song.serverAlbumId
            return primaryImageUrl(address, if (albumId != null && albumHasImage(address, credentials, albumId) == true) albumId else sibling)
        }

        val itemId = parseUrl(song.path)?.segments?.lastOrNull() ?: return null
        val result = authenticationManager.checkSession(credentials, itemsService.item(address, credentials.accessToken, credentials.userId, itemId))
        if (result !is NetworkResult.Success) return null
        val item = result.body
        val albumId = item.albumId
        val hasOwnImage = item.imageTags.containsKey("Primary")
        if (albumId == null) return if (hasOwnImage) primaryImageUrl(address, item.id) else null
        // An album with no image of its own 404s, while its songs may carry one each
        if (item.albumPrimaryImageTag != null || !hasOwnImage || albumHasImage(address, credentials, albumId) == true) return primaryImageUrl(address, albumId)
        return primaryImageUrl(address, item.id)
    }

    private data class AlbumKey(val address: String, val userId: String, val albumId: String)

    private val albumImages = mutableMapOf<AlbumKey, CompletableDeferred<Boolean?>>()

    private val albumImagesLock = Mutex()

    /** Whether the album has an image: null when the lookup failed, so callers use an image known to exist. Concurrent callers for one album share a request. */
    private suspend fun albumHasImage(
        address: String,
        credentials: AuthenticatedCredentials,
        albumId: String
    ): Boolean? {
        val key = AlbumKey(address, credentials.userId, albumId)
        var owned = false
        val lookup =
            albumImagesLock.withLock {
                albumImages[key] ?: CompletableDeferred<Boolean?>().also {
                    if (albumImages.size >= MAX_CACHED_ALBUMS) albumImages.clear()
                    albumImages[key] = it
                    owned = true
                }
            }
        if (!owned) return lookup.await()

        var hasImage: Boolean? = null
        try {
            val result = authenticationManager.checkSession(credentials, itemsService.item(address, credentials.accessToken, credentials.userId, albumId))
            if (result is NetworkResult.Success) {
                hasImage = result.body.let { it.albumPrimaryImageTag != null || it.imageTags.containsKey("Primary") }
            }
        } finally {
            // Only answers are remembered; a failure or cancellation is retried by the next caller
            withContext(NonCancellable) {
                if (hasImage == null) albumImagesLock.withLock { if (albumImages[key] === lookup) albumImages.remove(key) }
                lookup.complete(hasImage)
            }
        }
        return hasImage
    }

    private companion object {
        const val MAX_CACHED_ALBUMS = 2000
    }

    /** The artist item's own primary image: the song's item needn't be asked, the artist's id being known already. */
    override suspend fun getArtistArtworkUrl(
        song: Song,
        serverArtistId: String
    ): String? {
        val address = authenticationManager.getAddress() ?: return null
        if (authenticationManager.getAuthenticatedCredentials() == null) return null
        return primaryImageUrl(address, serverArtistId)
    }

    private fun primaryImageUrl(
        address: String,
        itemId: String
    ): String = "$address/Items/$itemId/Images/Primary?maxWidth=1000&maxHeight=1000"
}
