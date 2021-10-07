package com.simplecityapps.shuttle.ui.mediascanner

import com.simplecityapps.shuttle.ui.ViewModel
import com.simplecityapps.shuttle.inject.Inject
import com.simplecityapps.shuttle.inject.hilt.HiltViewModel
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.common.Progress

@HiltViewModel
class MediaScannerViewModel @Inject constructor(

) : ViewModel() {


}

sealed class ViewState() {
    class Idle : ViewState()
    class Importing(val importProgress: ImportProgress) : ViewState()
    class ImportComplete : ViewState()
}

sealed class ImportProgress(val mediaProviderType: MediaProviderType) {
    class SongImport(mediaProviderType: MediaProviderType, val song: Song, val progress: Progress) : ImportProgress(mediaProviderType)
    class PlaylistImport(mediaProviderType: MediaProviderType, val playlist: Playlist, val progress: Progress) : ImportProgress(mediaProviderType)
    class Complete(mediaProviderType: MediaProviderType) : ImportProgress(mediaProviderType)
    class Failure(mediaProviderType: MediaProviderType, val reason: Any) : ImportProgress(mediaProviderType)
}