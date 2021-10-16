package com.simplecityapps.shuttle.ui.mediaimporter

import com.simplecityapps.shuttle.inject.Inject
import com.simplecityapps.shuttle.inject.hilt.HiltViewModel
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Progress
import com.simplecityapps.shuttle.model.SongData
import com.simplecityapps.shuttle.ui.ViewModel
import com.simplecityapps.shuttle.ui.domain.model.GetUserSelectedMediaProviders
import com.simplecityapps.shuttle.ui.domain.model.ImportSongs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltViewModel
class MediaImporterViewModel @Inject constructor(
    private val getUserSelectedMediaProviders: GetUserSelectedMediaProviders,
    private val importSongs: ImportSongs
) : ViewModel() {

    private val _viewState = MutableStateFlow<ViewState>(ViewState.Loading)
    val viewState = _viewState.asStateFlow()

    fun import() {
        coroutineScope.launch {
            val mediaProviders = getUserSelectedMediaProviders()

            if (mediaProviders.isEmpty()) {
                _viewState.emit(
                    ViewState.Failed(
                        ViewState.Failed.Reason.NoMediaProviders
                    )
                )
            } else {
                _viewState.emit(
                    ViewState.ImportingMedia(
                        importStates = mediaProviders.associate { mediaProvider ->
                            mediaProvider.type to importSongs(mediaProvider)
                                .map { songImportState -> songImportState.toImportViewState() }
                        }
                    )
                )
            }
        }
    }
}

sealed class ViewState {
    object Loading : ViewState()

    data class ImportingMedia(
        val importStates: Map<MediaProviderType, Flow<ImportViewState>>
    ) : ViewState()

    data class Failed(val reason: Reason) : ViewState() {
        enum class Reason {
            NoMediaProviders
        }
    }
}

sealed class ImportViewState {
    object Loading : ImportViewState()
    data class QueryingApi(val progress: Progress) : ImportViewState()
    data class QueryingDatabase(val progress: Progress?) : ImportViewState()
    data class ReadingSongs(val progress: Progress, val songData: SongData) : ImportViewState()
    object UpdatingDatabase : ImportViewState()
    object Complete : ImportViewState()
    object Failure : ImportViewState()
}

fun ImportSongs.SongImportState.toImportViewState(): ImportViewState {
    return when (this) {
        is ImportSongs.SongImportState.QueryingApi -> {
            ImportViewState.QueryingApi(progress)
        }
        is ImportSongs.SongImportState.ReadingSongData -> {
            ImportViewState.ReadingSongs(progress, songData)
        }
        is ImportSongs.SongImportState.QueryingDatabase -> {
            ImportViewState.QueryingDatabase(progress)
        }
        is ImportSongs.SongImportState.UpdatingDatabase -> {
            ImportViewState.UpdatingDatabase
        }
        is ImportSongs.SongImportState.Complete -> {
            ImportViewState.Complete
        }
        is ImportSongs.SongImportState.Failed -> {
            ImportViewState.Failure
        }
    }
}