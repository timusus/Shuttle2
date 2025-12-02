package com.simplecityapps.shuttle.ui.screens.downloads

import com.simplecityapps.localmediaprovider.local.data.room.entity.DownloadData
import com.simplecityapps.mediaprovider.repository.downloads.DownloadRepository
import com.simplecityapps.mediaprovider.repository.songs.SongQuery
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.download.DownloadUseCase
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber

class DownloadsPresenter @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val songRepository: SongRepository,
    private val downloadUseCase: DownloadUseCase
) : BasePresenter<DownloadsContract.View>(),
    DownloadsContract.Presenter {

    override fun bindView(view: DownloadsContract.View) {
        super.bindView(view)
        loadDownloads()
    }

    override fun loadDownloads() {
        launch {
            try {
                view?.setLoadingState(true)

                combine(
                    downloadRepository.observeAllDownloads(),
                    songRepository.getSongs(SongQuery.All())
                ) { downloads, songs ->
                    Pair(downloads, songs)
                }.collect { (downloads, songs) ->
                    view?.setData(downloads, songs ?: emptyList())
                    view?.setLoadingState(false)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load downloads")
                view?.showLoadError(Error.LoadFailed(e))
                view?.setLoadingState(false)
            }
        }
    }

    override fun removeDownload(song: Song) {
        launch {
            try {
                downloadUseCase.removeDownload(song)
            } catch (e: Exception) {
                Timber.e(e, "Failed to remove download")
            }
        }
    }

    override fun pauseDownload(song: Song) {
        launch {
            try {
                downloadUseCase.pauseDownload(song)
            } catch (e: Exception) {
                Timber.e(e, "Failed to pause download")
            }
        }
    }

    override fun resumeDownload(song: Song) {
        launch {
            try {
                downloadUseCase.resumeDownload(song)
            } catch (e: Exception) {
                Timber.e(e, "Failed to resume download")
            }
        }
    }

    override fun removeAllDownloads() {
        launch {
            try {
                downloadUseCase.removeAllDownloads()
            } catch (e: Exception) {
                Timber.e(e, "Failed to remove all downloads")
            }
        }
    }

    sealed class Error {
        data class LoadFailed(val error: Throwable) : Error()
    }
}
