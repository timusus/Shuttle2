package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.userDescription
import com.simplecityapps.provider.jellyfin.http.ItemsService
import com.simplecityapps.provider.jellyfin.http.QueryResult
import com.simplecityapps.provider.jellyfin.http.items
import timber.log.Timber
import java.util.*

class JellyfinMediaProvider(
    private val authenticationManager: JellyfinAuthenticationManager,
    private val itemsService: ItemsService,
) : MediaProvider {

    override val type: MediaProvider.Type
        get() = MediaProvider.Type.Jellyfin

    override suspend fun findSongs(callback: ((song: Song, progress: Int, total: Int) -> Unit)?): List<Song> {
        val address = authenticationManager.getAddress() ?: return emptyList()
        return (authenticationManager.getAuthenticatedCredentials() ?: authenticationManager.getLoginCredentials()
            ?.let { loginCredentials -> authenticationManager.authenticate(address, loginCredentials).getOrNull() })
            ?.let { credentials ->
                when (val queryResult = itemsService.items(url = address, token = credentials.accessToken, userId = credentials.userId)) {
                    is NetworkResult.Success<QueryResult> -> {
                        return queryResult.body.items.map { item ->
                            Song(
                                id = item.id.toLongOrNull() ?: 0,
                                name = item.name,
                                albumArtist = item.albumArtist,
                                artists = item.artists,
                                album = item.album,
                                track = item.indexNumber ?: 0,
                                disc = 0,
                                duration = ((item.runTime ?: 0) / (10 * 1000)).toInt(),
                                year = item.productionYear ?: 0,
                                genres = item.genres,
                                path = "jellyfin://item/${item.id}",
                                size = 0,
                                mimeType = "Audio/*",
                                lastModified = Date(),
                                lastPlayed = null,
                                lastCompleted = null,
                                playCount = 0,
                                playbackPosition = 0,
                                blacklisted = false,
                                mediaStoreId = null,
                                mediaProvider = type
                            )
                        }
                    }
                    is NetworkResult.Failure -> {
                        Timber.e(queryResult.error, queryResult.error.userDescription())
                        return emptyList()
                    }
                }
            } ?: run {
            emptyList()
        }
    }
}