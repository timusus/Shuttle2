package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.*
import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.userDescription
import com.simplecityapps.provider.plex.http.ItemsService
import com.simplecityapps.provider.plex.http.QueryResult
import com.simplecityapps.provider.plex.http.items
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import timber.log.Timber
import java.util.*

class PlexMediaProvider(
    private val authenticationManager: PlexAuthenticationManager,
    private val itemsService: ItemsService,
) : MediaProvider {

    override val type: MediaProvider.Type
        get() = MediaProvider.Type.Plex


    override fun findSongs(): Flow<FlowEvent<List<Song>, MessageProgress>> {
        val address = authenticationManager.getAddress() ?: run {
            return flowOf(FlowEvent.Failure("Plex address unknown"))
        }

        return flow {
            (authenticationManager.getAuthenticatedCredentials() ?: authenticationManager.getLoginCredentials()
                ?.let { loginCredentials -> authenticationManager.authenticate(address, loginCredentials).getOrNull() })
                ?.let { credentials ->
                    when (val queryResult = itemsService.items(url = address, token = credentials.accessToken)) {
                        is NetworkResult.Success<QueryResult> -> {
                            emit(FlowEvent.Success(queryResult.body.metadata.items.map { item ->
                                Song(
                                    id = item.guid.hashCode().toLong(),
                                    name = item.title,
                                    albumArtist = item.grandparentTitle,
                                    artists = listOf(item.grandparentTitle),
                                    album = item.parentTitle,
                                    track = item.index ?: 0,
                                    disc = 0,
                                    duration = item.duration.toInt(),
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
                                    externalId = null,
                                    mediaProvider = type,
                                    lyrics = null,
                                    grouping = null,
                                    bitRate = null,
                                    bitDepth = null,
                                    sampleRate = null,
                                    channelCount = null
                                )
                            }))
                        }
                        is NetworkResult.Failure -> {
                            Timber.e(queryResult.error, queryResult.error.userDescription())
                            emit(FlowEvent.Failure(queryResult.error.userDescription()))
                        }
                    }
                } ?: run {
                emit(FlowEvent.Failure("Failed to authenticate"))
            }
        }
    }

    override fun findPlaylists(existingPlaylists: List<Playlist>, existingSongs: List<Song>): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>> {
        return flowOf(FlowEvent.Success(emptyList()))
    }
}