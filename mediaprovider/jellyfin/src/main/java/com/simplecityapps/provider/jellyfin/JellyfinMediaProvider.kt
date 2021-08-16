package com.simplecityapps.provider.jellyfin

import android.content.Context
import com.simplecityapps.mediaprovider.*
import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.userDescription
import com.simplecityapps.provider.jellyfin.http.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.*
import kotlin.math.min

class JellyfinMediaProvider(
    private val context: Context,
    private val authenticationManager: JellyfinAuthenticationManager,
    private val itemsService: ItemsService,
) : MediaProvider {

    override val type = MediaProvider.Type.Jellyfin

    override fun findSongs(): Flow<FlowEvent<List<Song>, MessageProgress>> {
        val address = authenticationManager.getAddress() ?: run {
            return flowOf(FlowEvent.Failure(context.getString(R.string.media_provider_address_missing)))
        }

        return flow {
            emit(FlowEvent.Progress(MessageProgress(context.getString(R.string.media_provider_querying_api), null)))
            authenticate(address)?.let { credentials ->
                emitAll(
                    queryItems(
                        address = address,
                        credentials = credentials
                    ).map { event ->
                        when (event) {
                            is FlowEvent.Success -> {
                                FlowEvent.Success(event.result.map { it.toSong() })
                            }
                            is FlowEvent.Progress -> {
                                FlowEvent.Progress(event.data)
                            }
                            is FlowEvent.Failure -> {
                                FlowEvent.Failure(event.message)
                            }
                        }
                    }
                )
            } ?: emit(FlowEvent.Failure(context.getString(R.string.media_provider_authentication_error)))
        }
    }

    override fun findPlaylists(
        existingPlaylists: List<Playlist>,
        existingSongs: List<Song>
    ): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>> {
        val address = authenticationManager.getAddress() ?: run {
            return flowOf(FlowEvent.Failure(context.getString(R.string.media_provider_address_missing)))
        }

        return flow {
            emit(FlowEvent.Progress(MessageProgress(context.getString(R.string.media_provider_querying_api), null)))
            authenticate(address)?.let { credentials ->
                when (val queryResult = itemsService.playlists(
                    url = address,
                    token = credentials.accessToken,
                    userId = credentials.userId
                )) {
                    is NetworkResult.Success<QueryResult> -> {
                        val updateData = mutableListOf<MediaImporter.PlaylistUpdateData>()
                        findSongsForPlaylists(address, credentials, queryResult.body.items, existingSongs).collectIndexed { index, playlistUpdateData ->
                            updateData.add(playlistUpdateData)
                        }
                        emit(FlowEvent.Success(updateData))
                    }
                    is NetworkResult.Failure -> {
                        Timber.e(queryResult.error, queryResult.error.userDescription())
                        emit(FlowEvent.Failure(queryResult.error.userDescription()))
                    }
                }
            } ?: emit(FlowEvent.Failure(context.getString(R.string.media_provider_authentication_error)))
        }
    }

    private suspend fun authenticate(address: String): AuthenticatedCredentials? {
        return (authenticationManager.getAuthenticatedCredentials()
            ?: authenticationManager.getLoginCredentials()
                ?.let { loginCredentials ->
                    authenticationManager.authenticate(
                        address,
                        loginCredentials
                    ).getOrNull()
                })
    }

    private fun queryItems(
        address: String,
        credentials: AuthenticatedCredentials,
        startIndex: Int = 0,
        pageSize: Int = 500,
        items: MutableList<Item> = mutableListOf()
    ): Flow<FlowEvent<List<Item>, MessageProgress>> {
        return flow {
            when (val queryResult = itemsService.audioItems(
                url = address,
                token = credentials.accessToken,
                userId = credentials.userId,
                limit = pageSize,
                startIndex = startIndex
            )) {
                is NetworkResult.Success<QueryResult> -> {
                    val totalRecordCount = queryResult.body.totalRecordCount
                    val lastIndex = startIndex + pageSize

                    emit(FlowEvent.Progress(MessageProgress(context.getString(R.string.media_provider_querying_api), Progress(lastIndex, totalRecordCount))))

                    items.addAll(queryResult.body.items)

                    if (lastIndex < totalRecordCount) {
                        emitAll(
                            queryItems(
                                address = address,
                                credentials = credentials,
                                startIndex = lastIndex,
                                pageSize = min(pageSize, totalRecordCount - lastIndex),
                                items = items
                            )
                        )
                    } else {
                        emit(FlowEvent.Success(items))
                    }
                }
                is NetworkResult.Failure -> {
                    Timber.e(queryResult.error, queryResult.error.userDescription())
                    emit(FlowEvent.Failure(queryResult.error.userDescription()))
                }
            }
        }
    }

    private fun findSongsForPlaylists(
        address: String,
        credentials: AuthenticatedCredentials,
        playlistItems: List<Item>,
        existingSongs: List<Song>
    ): Flow<MediaImporter.PlaylistUpdateData> {
        return playlistItems
            .asFlow()
            .flatMapConcat { playlistItem ->
                queryPlaylistItems(address, credentials, playlistItem.id)
                    .map { event ->
                        when (event) {
                            is FlowEvent.Success -> {
                                MediaImporter.PlaylistUpdateData(
                                    mediaProviderType = type,
                                    name = playlistItem.name ?: context.getString(R.string.unknown),
                                    songs = event.result.mapNotNull { item -> existingSongs.firstOrNull { it.externalId == item.id } },
                                    externalId = playlistItem.id
                                )
                            }
                            is FlowEvent.Failure -> null
                            is FlowEvent.Progress -> null
                        }
                    }
            }
            .filterNotNull()
    }

    private fun queryPlaylistItems(
        address: String,
        credentials: AuthenticatedCredentials,
        playlistId: String,
        startIndex: Int = 0,
        pageSize: Int = 500,
        items: MutableList<Item> = mutableListOf()
    ): Flow<FlowEvent<List<Item>, MessageProgress>> {
        return flow {
            when (val queryResult = itemsService.playlistItems(
                url = address,
                token = credentials.accessToken,
                playlistId = playlistId,
                limit = pageSize,
                startIndex = startIndex,
                userId = credentials.userId
            )) {
                is NetworkResult.Success<QueryResult> -> {
                    val totalRecordCount = queryResult.body.totalRecordCount
                    val lastIndex = startIndex + pageSize

                    emit(FlowEvent.Progress(MessageProgress(context.getString(R.string.media_provider_querying_api), Progress(lastIndex, totalRecordCount))))

                    items.addAll(queryResult.body.items)

                    if (lastIndex < totalRecordCount) {
                        emitAll(
                            queryItems(
                                address = address,
                                credentials = credentials,
                                startIndex = lastIndex,
                                pageSize = min(pageSize, totalRecordCount - lastIndex),
                                items = items
                            )
                        )
                    } else {
                        emit(FlowEvent.Success(items))
                    }
                }
                is NetworkResult.Failure -> {
                    Timber.e(queryResult.error, queryResult.error.userDescription())
                    emit(FlowEvent.Failure(queryResult.error.userDescription()))
                }
            }
        }
    }

    private fun Item.toSong(): Song {
        return Song(
            id = 0,
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
            externalId = id,
            mediaProvider = MediaProvider.Type.Jellyfin,
            lyrics = null,
            grouping = null,
            bitRate = null,
            bitDepth = null,
            sampleRate = null,
            channelCount = null
        )
    }
}