package com.simplecityapps.shuttle.ui.screens.downloads

import com.simplecityapps.localmediaprovider.local.data.room.entity.DownloadData
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract

interface DownloadsContract {

    sealed class Event {
        data class SongClicked(val song: Song) : Event()
        data class RemoveDownload(val song: Song) : Event()
        data class PauseDownload(val song: Song) : Event()
        data class ResumeDownload(val song: Song) : Event()
        object RemoveAllDownloads : Event()
        object NavigateBack : Event()
    }

    interface View : BaseContract.View<Presenter, Event> {
        fun setData(downloads: List<DownloadData>, songs: List<Song>)
        fun showLoadError(error: Error)
        fun setLoadingState(loading: Boolean)
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun loadDownloads()
        fun removeDownload(song: Song)
        fun pauseDownload(song: Song)
        fun resumeDownload(song: Song)
        fun removeAllDownloads()
    }
}
