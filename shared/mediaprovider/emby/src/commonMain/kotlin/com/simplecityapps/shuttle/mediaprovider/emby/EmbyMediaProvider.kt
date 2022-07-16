package com.simplecityapps.shuttle.mediaprovider.emby

import com.simplecityapps.shuttle.logging.logcat
import com.simplecityapps.shuttle.mediaprovider.common.MediaProvider
import com.simplecityapps.shuttle.mediaprovider.emby.http.data.AuthenticatedCredentials
import com.simplecityapps.shuttle.mediaprovider.emby.http.data.ItemResponse
import com.simplecityapps.shuttle.mediaprovider.emby.http.data.toSongData
import com.simplecityapps.shuttle.mediaprovider.emby.http.service.ItemsService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Progress
import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.flow.*

class EmbyMediaProvider(
    private val authenticationManager: EmbyAuthenticationManager,
    private val embyPreferenceManager: EmbyPreferenceManager,
    private val itemsService: ItemsService,
) : MediaProvider {

    override val type: MediaProviderType
        get() = MediaProviderType.Emby

    override fun findSongs(): Flow<MediaProvider.SongRetrievalState> {
        return flow {
            authenticationManager.authenticate().fold(
                { authenticatedCredentials ->
                    val address = embyPreferenceManager.getAddress().firstOrNull() ?: run {
                        logcat { "findSongs() failed: address null" }
                        emit(MediaProvider.SongRetrievalState.Failed)
                        return@flow
                    }
                    val items = queryItems(address, authenticatedCredentials)
                        .onEach { queryResponseProgress ->
                            emit(MediaProvider.SongRetrievalState.QueryingApi(queryResponseProgress.progress))
                        }
                        .last().items
                    emit(MediaProvider.SongRetrievalState.Complete(items.map { it.toSongData() }))
                },
                { throwable ->
                    logcat { "findSongs() failed: $throwable" }
                    emit(MediaProvider.SongRetrievalState.Failed)
                }
            )
        }.catch { throwable ->
            logcat { "findSongs() failed: $throwable" }
            emit(MediaProvider.SongRetrievalState.Failed)
        }
    }

    override fun findPlaylists(existingSongs: List<Song>): Flow<MediaProvider.PlaylistRetrievalState> {
        // Todo:
        return flowOf(MediaProvider.PlaylistRetrievalState.QueryingApi(Progress(0, 1)))
    }

    data class QueryResponseProgress(val items: List<ItemResponse>, val progress: Progress)

    private fun queryItems(
        address: String,
        credentials: AuthenticatedCredentials,
        startIndex: Int = 0,
        pageSize: Int = 500,
        items: MutableList<ItemResponse> = mutableListOf()
    ): Flow<QueryResponseProgress> {
        return flow {
            itemsService.audioItems(
                url = address,
                token = credentials.accessToken,
                userId = credentials.userId,
                limit = pageSize,
                startIndex = startIndex
            ).fold(
                onSuccess = { response ->
                    val lastIndex = startIndex + pageSize
                    emit(
                        QueryResponseProgress(
                            items = items + response.items,
                            progress = Progress(
                                progress = lastIndex,
                                total = response.totalRecordCount
                            )
                        )
                    )
                    if (lastIndex < response.totalRecordCount) {
                        emitAll(
                            queryItems(
                                address = address,
                                credentials = credentials,
                                startIndex = lastIndex,
                                pageSize = pageSize,
                                items = items
                            )
                        )
                    }
                },
                onFailure = { throwable ->
                    throw throwable
                }
            )
        }
    }
}