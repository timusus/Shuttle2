package com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider.taglib

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.saf.SafDirectoryHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DirectorySelectionViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _viewState = MutableStateFlow(DirectorySelectionViewState.Empty)
    val viewState = _viewState.map { state ->
        state.copy(
            isTraversalComplete = state.directories.all(
                DirectorySelectionContract.Directory::traversalComplete
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = DirectorySelectionViewState.Empty
    )

    fun onInitializeConfiguration() {
        val contentResolver = context.contentResolver
        context.contentResolver.persistedUriPermissions
            .filter { permission ->
                permission.isWritePermission || permission.isReadPermission
            }
            .forEach { permission ->
                parseUri(contentResolver, permission.uri)
            }
    }

    fun handleSafResult(
        contentResolver: ContentResolver,
        uri: Uri
    ) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        contentResolver.takePersistableUriPermission(uri, flags)
        parseUri(contentResolver, uri)
    }

    fun removeItem(directory: DirectorySelectionContract.Directory) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.releasePersistableUriPermission(directory.tree.rootUri, flags)
        _viewState.update { state ->
            state.copy(directories = state.directories - directory)
        }
    }

    private fun parseUri(
        contentResolver: ContentResolver,
        uri: Uri
    ) {
        viewModelScope.launch {
            SafDirectoryHelper.buildFolderNodeTree(contentResolver, uri)
                .collect { treeStatus ->
                    val directory = DirectorySelectionContract.Directory(
                        tree = treeStatus.tree,
                        traversalComplete = treeStatus is SafDirectoryHelper.TreeStatus.Complete
                    )
                    _viewState.update { state ->
                        val currentList = state.directories.toMutableList()
                        val index = currentList.indexOfFirst {
                            it.tree.rootUri == directory.tree.rootUri
                        }
                        if (index == -1) {
                            currentList += directory
                        } else {
                            currentList[index] = directory
                        }
                        state.copy(directories = currentList)
                    }
                }
        }
    }
}
