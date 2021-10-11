package com.simplecityapps.shuttle.ui.mediaimporter

import com.simplecityapps.shuttle.inject.Inject
import com.simplecityapps.shuttle.inject.hilt.HiltViewModel
import com.simplecityapps.shuttle.mediaimport.model.GetUserSelectedMediaProviders
import com.simplecityapps.shuttle.mediaimport.model.songs.ImportSongs
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Progress
import com.simplecityapps.shuttle.model.SongData
import com.simplecityapps.shuttle.ui.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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
                val mediaProviderTypes = mediaProviders.map { mediaProvider -> mediaProvider.type }
                val importStates: MutableList<ImportViewState> = mediaProviderTypes.map { ImportViewState.Loading(it) }.toMutableList()

                _viewState.emit(
                    ViewState.ImportingMedia(
                        importStates = importStates
                    )
                )

                importSongs(mediaProviders).collect { songImportState ->
                    importStates.removeAll { it.mediaProviderType == songImportState.mediaProviderType }
                    importStates.add(songImportState.toImportViewState())

                    _viewState.emit(
                        ViewState.ImportingMedia(
                            importStates = importStates
                        )
                    )
                }
            }
        }
    }
}

sealed class ViewState {
    object Loading : ViewState()
    data class ImportingMedia(val importStates: List<ImportViewState>) : ViewState()
    data class Failed(val reason: Reason) : ViewState() {
        enum class Reason {
            NoMediaProviders
        }
    }
}

sealed class ImportViewState(open val mediaProviderType: MediaProviderType) {
    data class Loading(override val mediaProviderType: MediaProviderType) : ImportViewState(mediaProviderType)
    data class QueryingApi(override val mediaProviderType: MediaProviderType, val progress: Progress) : ImportViewState(mediaProviderType)
    data class ReadingSongs(override val mediaProviderType: MediaProviderType, val progress: Progress, val songData: SongData) : ImportViewState(mediaProviderType)
    data class UpdatingDatabase(override val mediaProviderType: MediaProviderType) : ImportViewState(mediaProviderType)
    data class Complete(override val mediaProviderType: MediaProviderType) : ImportViewState(mediaProviderType)
    data class Failure(override val mediaProviderType: MediaProviderType) : ImportViewState(mediaProviderType)
}

fun ImportSongs.SongImportState.toImportViewState(): ImportViewState {
    return when (this) {
        is ImportSongs.SongImportState.QueryingApi -> {
            ImportViewState.QueryingApi(mediaProviderType, progress)
        }
        is ImportSongs.SongImportState.ReadingSongData -> {
            ImportViewState.ReadingSongs(mediaProviderType, progress, songData)
        }
        is ImportSongs.SongImportState.UpdatingDatabase -> {
            ImportViewState.UpdatingDatabase(mediaProviderType)
        }
        is ImportSongs.SongImportState.Complete -> {
            ImportViewState.Complete(mediaProviderType)
        }
        is ImportSongs.SongImportState.Failed -> {
            ImportViewState.Failure(mediaProviderType)
        }
    }
}