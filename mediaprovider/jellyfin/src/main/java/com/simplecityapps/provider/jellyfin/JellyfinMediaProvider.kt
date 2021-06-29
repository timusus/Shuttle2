package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.userDescription
import com.simplecityapps.provider.jellyfin.http.*
import timber.log.Timber
import java.util.*
import kotlin.math.min

class JellyfinMediaProvider(
    private val authenticationManager: JellyfinAuthenticationManager,
    private val itemsService: ItemsService,
) : MediaProvider {

    override val type: MediaProvider.Type
        get() = MediaProvider.Type.Jellyfin

    override suspend fun findSongs(callback: ((song: Song, progress: Int, total: Int) -> Unit)?): List<Song>? {
        val address = authenticationManager.getAddress() ?: return emptyList()
        return (authenticationManager.getAuthenticatedCredentials() ?: authenticationManager.getLoginCredentials()
            ?.let { loginCredentials -> authenticationManager.authenticate(address, loginCredentials).getOrNull() })
            ?.let { credentials -> queryItems(address, credentials, 0, 2500) }
    }

    suspend fun queryItems(address: String, credentials: AuthenticatedCredentials, index: Int, pageSize: Int): List<Song>? {
        return when (val queryResult = itemsService.items(
            url = address,
            token = credentials.accessToken,
            userId = credentials.userId,
            limit = pageSize,
            index = index
        )) {
            is NetworkResult.Success<QueryResult> -> {
                val songs = queryResult.body.items.map { it.toSong() }.toMutableList()
                val totalRecordCount = queryResult.body.totalRecordCount
                val lastIndex = index + pageSize
                if (lastIndex < totalRecordCount) {
                    queryItems(address, credentials, lastIndex, min(pageSize, totalRecordCount - lastIndex))?.let {
                        songs.addAll(it)
                    } ?: run {
                        return null
                    }
                }
                songs
            }
            is NetworkResult.Failure -> {
                Timber.e(queryResult.error, queryResult.error.userDescription())
                return null
            }
        }
    }

    fun Item.toSong(): Song {
        return Song(
            id = id.toLongOrNull() ?: 0,
            name = name,
            albumArtist = albumArtist,
            artists = artists.filter { it.isNotEmpty() },
            album = album,
            track = indexNumber,
            disc = parentIndexNumber,
            duration = ((runTime ?: 0) / (10 * 1000)).toInt(),
            year = productionYear,
            genres = genres,
            path = "jellyfin://item/${id}",
            size = 0,
            mimeType = "Audio/*",
            lastModified = Date(),
            lastPlayed = null,
            lastCompleted = null,
            playCount = 0,
            playbackPosition = 0,
            blacklisted = false,
            mediaStoreId = null,
            mediaProvider = MediaProvider.Type.Jellyfin,
            lyrics = null,
            grouping = null,
        )
    }
}