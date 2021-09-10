package com.simplecityapps.shuttle.ui.screens.onboarding.taglib

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.simplecityapps.saf.DocumentNodeTree
import com.simplecityapps.saf.SafDirectoryHelper
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

interface DirectorySelectionContract {

    data class Directory(val tree: DocumentNodeTree, val traversalComplete: Boolean, val hasWritePermission: Boolean)

    interface Presenter : BaseContract.Presenter<View> {
        fun loadData(contentResolver: ContentResolver)
        fun removeItem(directory: Directory)
        fun handleSafResult(contentResolver: ContentResolver, intent: Intent)
        fun presentDocumentProvider()
    }

    interface View {
        fun setData(data: List<Directory>)
        fun startActivity(intent: Intent, requestCode: Int)
        fun showDocumentProviderNotAvailable()
    }
}

class DirectorySelectionPresenter @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : DirectorySelectionContract.Presenter, BasePresenter<DirectorySelectionContract.View>() {

    private var data: MutableList<DirectorySelectionContract.Directory> = mutableListOf()

    override fun loadData(contentResolver: ContentResolver) {
        val uris = contentResolver.persistedUriPermissions.filter { uriPermission -> uriPermission.isWritePermission || uriPermission.isReadPermission }
        if (uris.isEmpty()) {
            setData(data)
        } else {
            uris.forEach { uriPermission ->
                parseUri(contentResolver, uriPermission.uri, uriPermission.isWritePermission)
            }
        }
    }

    override fun removeItem(directory: DirectorySelectionContract.Directory) {
        try {
            context.contentResolver?.releasePersistableUriPermission(directory.tree.rootUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (e: SecurityException) {
            Timber.e("Failed to release persistable uri permission: ${directory.tree.rootUri}")
        }
        data.remove(directory)
        setData(data)
    }

    override fun handleSafResult(contentResolver: ContentResolver, intent: Intent) {
        intent.data?.let { uri ->
            contentResolver.takePersistableUriPermission(uri, intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
            parseUri(contentResolver, uri, true)
        }
    }

    override fun presentDocumentProvider() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        if (intent.resolveActivity(context.packageManager) != null) {
            view?.startActivity(intent, DirectorySelectionFragment.REQUEST_CODE_OPEN_DOCUMENT)
        } else {
            view?.showDocumentProviderNotAvailable()
        }
    }

    private fun parseUri(contentResolver: ContentResolver, uri: Uri, hasWritePermission: Boolean) {
        appCoroutineScope.launch {
            SafDirectoryHelper.buildFolderNodeTree(contentResolver, uri)
                .collect { treeStatus ->
                    val directory = DirectorySelectionContract.Directory(
                        tree = treeStatus.tree,
                        traversalComplete = treeStatus is SafDirectoryHelper.TreeStatus.Complete,
                        hasWritePermission = hasWritePermission
                    )
                    val index = data.indexOfFirst { it.tree == treeStatus.tree }
                    if (index == -1) {
                        data.add(directory)
                    } else {
                        data[index] = directory
                    }
                    setData(data)
                }
        }
    }

    fun setData(directories: List<DirectorySelectionContract.Directory>) {
        view?.setData(directories)
    }
}