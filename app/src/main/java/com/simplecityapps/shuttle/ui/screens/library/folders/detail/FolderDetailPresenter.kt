package com.simplecityapps.shuttle.ui.screens.library.folders.detail

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.simplecityappds.saf.SafDirectoryHelper
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.shuttle.ui.common.error.UserFriendlyError
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.simplecityapps.shuttle.ui.screens.library.folders.FileNode
import com.simplecityapps.shuttle.ui.screens.library.folders.FileNodeTree
import com.simplecityapps.shuttle.ui.screens.library.folders.find
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.net.URLDecoder
import java.nio.charset.Charset
import javax.inject.Inject

interface FolderDetailContract {

    interface Presenter : BaseContract.Presenter<View> {
        fun loadData(uri: Uri)
        fun onSongClicked(song: Song, songs: List<Song>)
        fun addToQueue(song: Song)
        fun playNext(song: Song)
        fun blacklist(song: Song)
        fun delete(song: Song)
    }

    interface View {
        fun setData(nodes: List<SafDirectoryHelper.FileNode>)
        fun showLoadError(error: Error)
        fun onAddedToQueue(song: Song)
        fun showDeleteError(error: Error)
    }
}

class FolderDetailPresenter @Inject constructor(
    private val context: Context,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager
) : BasePresenter<FolderDetailContract.View>(), FolderDetailContract.Presenter {

    private lateinit var single: Single<FileNodeTree>

    private fun getRoot(): Single<FileNodeTree> {
        if (!::single.isInitialized) {
            single = songRepository
                .getSongs()
                .first(emptyList())
                .map { songs ->

                    val root = FileNodeTree(Uri.parse("/"), "Root")
                    var currentTree = root

                    songs
                        .sortedBy { song -> song.path }
                        .forEach { song ->

                            val path = song.path.sanitise()
                            val parts = path.split(Regex("(?<!/)/(?!/)"))

                            parts.forEachIndexed { index, part ->
                                var path = currentTree.uri.toString().sanitise()
                                path += part

                                if (index != parts.size - 1) {
                                    if (path != "/") {
                                        path += "/"
                                    }
                                    currentTree = currentTree.addTreeNode(FileNodeTree(Uri.parse(path), part))
                                } else {
                                    currentTree.addLeafNode(FileNode(Uri.parse(path), part, song, currentTree))
                                }
                            }
                            currentTree = root
                        }
                    root
                }
                .cache()
        }

        return single
    }

    override fun loadData(uri: Uri) {
        addDisposable(
            getRoot()
                .flatMap { root ->
                    root.find(uri)?.let { Single.just(it) } ?: Single.error(Error("Uri $uri not found in parent FileNodeTree"))
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = { fileNodeTree ->
                        view?.setData(fileNodeTree.treeNodes.toList() + fileNodeTree.leafNodes.toList())
                    },
                    onError = { throwable ->
                        Timber.e(throwable, "Failed to load folders")
                    }
                )
        )
    }

    override fun onSongClicked(song: Song, songs: List<Song>) {
        playbackManager.load(songs, songs.indexOf(song)) { result ->
            result.onSuccess { playbackManager.play() }
            result.onFailure { error -> view?.showLoadError(error as Error) }
        }
    }

    override fun addToQueue(song: Song) {
        playbackManager.addToQueue(listOf(song))
        view?.onAddedToQueue(song)
    }

    override fun playNext(song: Song) {
        playbackManager.playNext(listOf(song))
        view?.onAddedToQueue(song)
    }

    override fun blacklist(song: Song) {
        addDisposable(
            songRepository.setBlacklisted(listOf(song), true)
                .subscribeOn(Schedulers.io())
                .subscribeBy(onError = { throwable -> Timber.e(throwable, "Failed to blacklist song") })
        )
    }

    override fun delete(song: Song) {
        val uri = song.path.toUri()
        val documentFile = DocumentFile.fromSingleUri(context, uri)
        if (documentFile?.delete() == true) {
            addDisposable(songRepository.removeSong(song)
                .subscribeOn(Schedulers.io())
                .subscribeBy(
                    onComplete = { Timber.i("Song deleted") },
                    onError = { throwable -> Timber.e(throwable, "Failed to remove song from database") }
                ))
        } else {
            view?.showDeleteError(UserFriendlyError("The song couldn't be deleted"))
        }
    }
}


fun String.sanitise(): String {

    var path = this

    if (this.contains("document/")) {
        path = this.substring(this.indexOf("document/") + "document/".length)
    }

    try {
        path = URLDecoder.decode(path, Charset.forName("UTF-8").name())
    } catch (ignored: Exception) {

    }

    if (path.startsWith("/")) {
        path = path.substring(path.indexOf("/") + 1)
    }

    return path
}