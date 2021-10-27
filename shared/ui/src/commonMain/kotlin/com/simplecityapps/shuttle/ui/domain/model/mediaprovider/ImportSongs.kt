package com.simplecityapps.shuttle.ui.domain.model.mediaprovider

import com.simplecityapps.shuttle.inject.Inject
import com.simplecityapps.shuttle.mediaprovider.common.MediaProvider
import com.simplecityapps.shuttle.model.InsertSongs
import com.simplecityapps.shuttle.model.Progress
import com.simplecityapps.shuttle.model.SongData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

class ImportSongs @Inject constructor(
    val insertSongs: InsertSongs
) {

    sealed class SongImportState {
        data class QueryingApi(val progress: Progress) : SongImportState()
        data class QueryingDatabase(val progress: Progress?) : SongImportState()
        data class ReadingSongData(val progress: Progress, val songData: SongData) : SongImportState()
        object UpdatingDatabase : SongImportState()
        data class Complete(val songs: List<SongData>) : SongImportState()
        object Failed : SongImportState()
    }

    operator fun invoke(mediaProvider: MediaProvider): Flow<SongImportState> {
        return flow {
            mediaProvider.findSongs().collect { songRetrievalState ->
                when (songRetrievalState) {
                    is MediaProvider.SongRetrievalState.QueryingApi -> {
                        emit(
                            SongImportState.QueryingApi(
                                progress = songRetrievalState.progress
                            )
                        )
                    }
                    is MediaProvider.SongRetrievalState.QueryingDatabase ->  emit(
                        SongImportState.QueryingDatabase(
                            progress = songRetrievalState.progress
                        )
                    )

                    is MediaProvider.SongRetrievalState.ReadingSongData -> {
                        emit(
                            SongImportState.ReadingSongData(
                                progress = songRetrievalState.progress,
                                songData = songRetrievalState.songData
                            )
                        )
                    }
                    is MediaProvider.SongRetrievalState.Complete -> {
                        emit(SongImportState.UpdatingDatabase)

                        insertSongs(songRetrievalState.songData)

                        emit(
                            SongImportState.Complete(
                                songs = songRetrievalState.songData
                            )
                        )
                    }
                    is MediaProvider.SongRetrievalState.Failed -> {
                        emit(SongImportState.Failed)
                    }
                }
            }
        }
    }
}