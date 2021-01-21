package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.userDescription
import com.simplecityapps.provider.plex.http.ItemsService
import com.simplecityapps.provider.plex.http.QueryResult
import com.simplecityapps.provider.plex.http.items
import timber.log.Timber
import java.util.*

class PlexMediaProvider(
    private val authenticationManager: PlexAuthenticationManager,
    private val itemsService: ItemsService,
) : MediaProvider {

    override val type: MediaProvider.Type
        get() = MediaProvider.Type.Plex

    override suspend fun findSongs(callback: ((song: Song, progress: Int, total: Int) -> Unit)?): List<Song> {
        val address = authenticationManager.getAddress() ?: return emptyList()
        return (authenticationManager.getAuthenticatedCredentials() ?: authenticationManager.getLoginCredentials()
            ?.let { loginCredentials -> authenticationManager.authenticate(address, loginCredentials).getOrNull() })
            ?.let { credentials ->
                when (val queryResult = itemsService.items(url = address, token = credentials.accessToken)) {
                    is NetworkResult.Success<QueryResult> -> {
                        return queryResult.body.metadata.items.map { item ->
                            Song(
                                id = item.guid.hashCode().toLong(),
                                name = item.title ?: "Unknown",
                                albumArtist = item.grandparentTitle ?: "Unknown",
                                artist = item.grandparentTitle ?: "Unknown",
                                album = item.parentTitle ?: "Unknown",
                                track = item.index ?: 0,
                                disc = 0,
                                duration = (item.duration ?: 0).toInt(),
                                year = 0,
                                genres = emptyList(),
                                path = "plex://${item.key}",
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