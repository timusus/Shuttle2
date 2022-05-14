package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.userDescription
import com.simplecityapps.provider.plex.http.ItemsService
import com.simplecityapps.provider.plex.http.QueryResult
import com.simplecityapps.provider.plex.http.items
import com.simplecityapps.provider.plex.http.sections
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import timber.log.Timber

class PlexMediaProvider(
    private val authenticationManager: PlexAuthenticationManager,
    private val itemsService: ItemsService,
) : MediaProvider {

    override val type: MediaProviderType
        get() = MediaProviderType.Plex

    override fun findSongs(): Flow<FlowEvent<List<Song>, MessageProgress>> {
        val address = authenticationManager.getAddress() ?: run {
            return flowOf(FlowEvent.Failure("Plex address unknown"))
        }

        return flow {
            (
                authenticationManager.getAuthenticatedCredentials() ?: authenticationManager.getLoginCredentials()
                    ?.let { loginCredentials -> authenticationManager.authenticate(address, loginCredentials).getOrNull() }
                )
                ?.let { credentials ->
                    when (val result = itemsService.sections(url = address, token = credentials.accessToken)) {
                        is NetworkResult.Success<QueryResult> -> {
                            result.body.mediaContainer.directories?.firstOrNull { it.title.equals("music", true) }?.key?.let { section ->
                                when (val queryResult = itemsService.items(url = address, token = credentials.accessToken, section = section)) {
                                    is NetworkResult.Success<QueryResult> -> {
                                        emit(
                                            FlowEvent.Success(
                                                queryResult.body.mediaContainer.metadata.orEmpty().map { metadata ->
                                                    Song(
                                                        id = metadata.guid.hashCode().toLong(),
                                                        name = metadata.title,
                                                        albumArtist = metadata.grandparentTitle,
                                                        artists = listOf(metadata.grandparentTitle),
                                                        album = metadata.parentTitle,
                                                        track = metadata.index ?: 0,
                                                        disc = metadata.parentIndex ?: 0,
                                                        duration = metadata.duration.toInt(),
                                                        date = metadata.year?.let { LocalDate(it, 1, 1) },
                                                        genres = emptyList(),
                                                        path = "plex://${metadata.key}",
                                                        size = metadata.media.firstOrNull()?.parts?.firstOrNull()?.size?.toLong() ?: 0L,
                                                        mimeType = "Audio/*",
                                                        lastModified = Clock.System.now(),
                                                        lastPlayed = null,
                                                        lastCompleted = null,
                                                        playCount = 0,
                                                        playbackPosition = 0,
                                                        blacklisted = false,
                                                        externalId = metadata.media.firstOrNull()?.parts?.firstOrNull()?.key,
                                                        mediaProvider = type,
                                                        lyrics = null,
                                                        grouping = null,
                                                        bitRate = metadata.media.firstOrNull()?.bitrate,
                                                        bitDepth = null,
                                                        sampleRate = null,
                                                        channelCount = metadata.media.firstOrNull()?.audioChannels
                                                    )
                                                }
                                            )
                                        )
                                    }
                                    is NetworkResult.Failure -> {
                                        Timber.e(queryResult.error, queryResult.error.userDescription())
                                        emit(FlowEvent.Failure(queryResult.error.userDescription()))
                                    }
                                }
                            } ?: run {
                                Timber.e("Failed to find 'music' section")
                                emit(FlowEvent.Failure("Failed to find Plex 'music' library"))
                            }
                        }
                        is NetworkResult.Failure -> {
                            Timber.e(result.error, result.error.userDescription())
                            emit(FlowEvent.Failure(result.error.userDescription()))
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
