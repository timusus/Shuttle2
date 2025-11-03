package com.simplecityapps.provider.emby

import android.content.Context
import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.R
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.userDescription
import com.simplecityapps.provider.emby.http.AuthenticatedCredentials
import com.simplecityapps.provider.emby.http.Item
import com.simplecityapps.provider.emby.http.ItemsService
import com.simplecityapps.provider.emby.http.QueryResult
import com.simplecityapps.provider.emby.http.audioItems
import com.simplecityapps.provider.emby.http.playlistItems
import com.simplecityapps.provider.emby.http.playlists
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import kotlin.math.min
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import timber.log.Timber

class EmbyMediaProvider(
    private val context: Context,
    private val authenticationManager: EmbyAuthenticationManager,
    private val itemsService: ItemsService
) : MediaProvider {
    override val type = MediaProviderType.Emby

    override fun findSongs(): Flow<FlowEvent<List<Song>, MessageProgress>> {
        val address =
            authenticationManager.getAddress() ?: run {
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
        val address =
            authenticationManager.getAddress() ?: run {
                return flowOf(FlowEvent.Failure(context.getString(R.string.media_provider_address_missing)))
            }

        return flow {
            emit(FlowEvent.Progress(MessageProgress(context.getString(R.string.media_provider_querying_api), null)))
            authenticate(address)?.let { credentials ->
                when (
                    val queryResult =
                        itemsService.playlists(
                            url = address,
                            token = credentials.accessToken,
                            userId = credentials.userId
                        )
                ) {
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

    private suspend fun authenticate(address: String): AuthenticatedCredentials? = (
        authenticationManager.getAuthenticatedCredentials()
            ?: authenticationManager.getLoginCredentials()
                ?.let { loginCredentials ->
                    authenticationManager.authenticate(
                        address,
                        loginCredentials
                    ).getOrNull()
                }
        )

    private fun queryItems(
        address: String,
        credentials: AuthenticatedCredentials,
        startIndex: Int = 0,
        pageSize: Int = 500,
        items: MutableList<Item> = mutableListOf()
    ): Flow<FlowEvent<List<Item>, MessageProgress>> = flow {
        when (
            val queryResult =
                itemsService.audioItems(
                    url = address,
                    token = credentials.accessToken,
                    userId = credentials.userId,
                    limit = pageSize,
                    startIndex = startIndex
                )
        ) {
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun findSongsForPlaylists(
        address: String,
        credentials: AuthenticatedCredentials,
        playlistItems: List<Item>,
        existingSongs: List<Song>
    ): Flow<MediaImporter.PlaylistUpdateData> = playlistItems
        .asFlow()
        .flatMapConcat { playlistItem ->
            queryPlaylistItems(address, credentials, playlistItem.id)
                .map { event ->
                    when (event) {
                        is FlowEvent.Success -> {
                            val matchingSongs = event.result.mapNotNull { item -> existingSongs.firstOrNull { item.id == it.externalId } }
                            MediaImporter.PlaylistUpdateData(
                                mediaProviderType = type,
                                name = playlistItem.name ?: context.getString(com.simplecityapps.core.R.string.unknown),
                                songs = matchingSongs,
                                externalId = playlistItem.id
                            )
                        }

                        is FlowEvent.Failure -> null
                        is FlowEvent.Progress -> null
                    }
                }
        }
        .filterNotNull()

    private fun queryPlaylistItems(
        address: String,
        credentials: AuthenticatedCredentials,
        playlistId: String,
        startIndex: Int = 0,
        pageSize: Int = 500,
        items: MutableList<Item> = mutableListOf()
    ): Flow<FlowEvent<List<Item>, MessageProgress>> = flow {
        when (
            val queryResult =
                itemsService.playlistItems(
                    url = address,
                    token = credentials.accessToken,
                    playlistId = playlistId,
                    limit = pageSize,
                    startIndex = startIndex,
                    userId = credentials.userId
                )
        ) {
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

    private fun Item.toSong(): Song = Song(
        id = 0,
        name = name,
        albumArtist = albumArtist,
        artists = artists.filter { it.isNotEmpty() },
        album = album,
        track = indexNumber,
        disc = parentIndexNumber,
        duration = ((runTime ?: 0) / (10 * 1000)).toInt(),
        date = productionYear?.let { year -> LocalDate(year, 1, 1) },
        genres = genres,
        path = "emby://item/$id",
        size = 0,
        mimeType = "Audio/*",
        lastModified = Clock.System.now(),
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        externalId = id,
        mediaProvider = MediaProviderType.Emby,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )
}
