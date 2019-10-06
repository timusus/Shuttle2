package com.simplecityapps.shuttle.ui.screens.onboarding.directories

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.simplecityappds.saf.SafDirectoryHelper
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject

interface MusicDirectoriesContract {

    interface Presenter : BaseContract.Presenter<View> {
        fun loadData(contentResolver: ContentResolver)
        fun removeItem(data: View.Data)
        fun handleSafResult(contentResolver: ContentResolver, intent: Intent)
    }

    interface View {
        class Data(val tree: SafDirectoryHelper.DocumentNodeTree, val traversalComplete: Boolean) {

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Data

                if (tree != other.tree) return false

                return true
            }

            override fun hashCode(): Int {
                return tree.hashCode()
            }
        }

        fun setData(data: List<Data>)
    }
}

class MusicDirectoriesPresenter @Inject constructor(
    private val context: Context
) : MusicDirectoriesContract.Presenter, BasePresenter<MusicDirectoriesContract.View>() {

    private var data = mutableListOf<MusicDirectoriesContract.View.Data>()

    override fun loadData(contentResolver: ContentResolver) {
        contentResolver.persistedUriPermissions
            .filter { uriPermission -> uriPermission.isReadPermission }
            .forEach { uriPermission ->
                parseUri(contentResolver, uriPermission.uri)
            }
        view?.setData(data)
    }

    override fun removeItem(data: MusicDirectoriesContract.View.Data) {
        context.contentResolver?.releasePersistableUriPermission(data.tree.rootUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val index = this.data.indexOf(data)
        if (index != -1) {
            this.data.removeAt(index)
        }
        view?.setData(this.data)
    }

    override fun handleSafResult(contentResolver: ContentResolver, intent: Intent) {
        intent.data?.let { uri ->
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            parseUri(contentResolver, uri)
        }
    }

    private fun parseUri(contentResolver: ContentResolver, uri: Uri) {
        addDisposable(
            Observable.create<MusicDirectoriesContract.View.Data> { emitter ->
                SafDirectoryHelper.buildFolderNodeTree(contentResolver, uri) { tree, traversalComplete ->
                    tree?.let { tree ->
                        emitter.onNext(MusicDirectoriesContract.View.Data(tree, traversalComplete))
                        if (traversalComplete) {
                            emitter.onComplete()
                        }
                    } ?: emitter.onComplete()
                }
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onNext = { element ->
                        val index = data.indexOf(element)
                        if (index != -1) {
                            data[index] = element
                        } else {
                            data.add(element)
                        }
                        view?.setData(data)
                    },
                    onError = { error -> Timber.e(error, "Failed to parse uri: $uri") })
        )
    }
}