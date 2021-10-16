package com.simplecityapps.shuttle.ui.domain.model

import com.simplecityapps.shuttle.inject.Inject
import com.simplecityapps.shuttle.mediaprovider.common.MediaProvider
import com.simplecityapps.shuttle.model.InsertPlaylists
import com.simplecityapps.shuttle.model.PlaylistData
import com.simplecityapps.shuttle.model.Progress
import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

class ImportPlaylists @Inject constructor(
    val insertPlaylists: InsertPlaylists
) {

    sealed class PlaylistImportState {
        data class QueryingApi(val progress: Progress) : PlaylistImportState()
        data class QueryingDatabase(val progress: Progress?) : PlaylistImportState()
        object UpdatingDatabase : PlaylistImportState()
        data class Complete(val songs: List<PlaylistData>) : PlaylistImportState()
        object Failed : PlaylistImportState()
    }

    operator fun invoke(mediaProvider: MediaProvider, songs: List<Song>): Flow<PlaylistImportState> {
        return flow {
            mediaProvider.findPlaylists(songs).collect { playlistRetrievalState ->
                when (playlistRetrievalState) {
                    is MediaProvider.PlaylistRetrievalState.QueryingApi -> {
                        emit(
                            PlaylistImportState.QueryingApi(
                                progress = playlistRetrievalState.progress
                            )
                        )
                    }
                    is MediaProvider.PlaylistRetrievalState.QueryingDatabase -> emit(
                        PlaylistImportState.QueryingDatabase(
                            progress = playlistRetrievalState.progress
                        )
                    )
                    is MediaProvider.PlaylistRetrievalState.Complete -> {
                        emit(PlaylistImportState.UpdatingDatabase)

                        insertPlaylists(playlistRetrievalState.playlistData)

                        emit(
                            PlaylistImportState.Complete(
                                songs = playlistRetrievalState.playlistData
                            )
                        )
                    }
                    is MediaProvider.PlaylistRetrievalState.Failed -> {
                        emit(PlaylistImportState.Failed)
                    }
                }
            }
        }
    }
}