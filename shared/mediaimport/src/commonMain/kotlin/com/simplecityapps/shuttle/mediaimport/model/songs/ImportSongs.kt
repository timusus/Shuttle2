package com.simplecityapps.shuttle.mediaimport.model.songs

import com.simplecityapps.shuttle.inject.Inject
import com.simplecityapps.shuttle.mediaimport.MediaProvider
import com.simplecityapps.shuttle.model.InsertSongs
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Progress
import com.simplecityapps.shuttle.model.SongData
import kotlinx.coroutines.flow.*

class ImportSongs @Inject constructor(
    val insertSongs: InsertSongs
) {

    sealed class SongImportState(open val mediaProviderType: MediaProviderType) {
        data class QueryingApi(override val mediaProviderType: MediaProviderType, val progress: Progress) : SongImportState(mediaProviderType)
        data class ReadingSongData(override val mediaProviderType: MediaProviderType, val progress: Progress, val songData: SongData) : SongImportState(mediaProviderType)
        data class UpdatingDatabase(override val mediaProviderType: MediaProviderType) : SongImportState(mediaProviderType)
        data class Complete(override val mediaProviderType: MediaProviderType, val songs: List<SongData>) : SongImportState(mediaProviderType)
        data class Failed(override val mediaProviderType: MediaProviderType) : SongImportState(mediaProviderType)
    }

    operator fun invoke(mediaProviders: List<MediaProvider>): Flow<SongImportState> {
        return mediaProviders.asFlow().flatMapConcat { mediaProvider ->
            importSongs(mediaProvider)
        }
    }

    private fun importSongs(mediaProvider: MediaProvider): Flow<SongImportState> {
        return flow {
            mediaProvider.findSongs().collect { songRetrievalState ->
                when (songRetrievalState) {
                    is MediaProvider.SongRetrievalState.QueryingApi -> {
                        emit(
                            SongImportState.QueryingApi(
                                mediaProviderType = mediaProvider.type,
                                progress = songRetrievalState.progress
                            )
                        )
                    }
                    is MediaProvider.SongRetrievalState.ReadingSongData -> {
                        emit(
                            SongImportState.ReadingSongData(
                                mediaProviderType = mediaProvider.type,
                                progress = songRetrievalState.progress,
                                songData = songRetrievalState.songData
                            )
                        )
                    }
                    is MediaProvider.SongRetrievalState.Complete -> {
                        emit(
                            SongImportState.UpdatingDatabase(
                                mediaProviderType = mediaProvider.type
                            )
                        )

                        insertSongs(songRetrievalState.songData)

                        emit(
                            SongImportState.Complete(
                                mediaProviderType = mediaProvider.type,
                                songs = songRetrievalState.songData
                            )
                        )
                    }
                    MediaProvider.SongRetrievalState.Failed -> {
                        emit(
                            SongImportState.Failed(
                                mediaProviderType = mediaProvider.type
                            )
                        )
                    }
                }
            }
        }
    }
}