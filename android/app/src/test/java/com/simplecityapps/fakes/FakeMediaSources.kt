package com.simplecityapps.fakes

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.screens.sources.FolderKind
import com.simplecityapps.shuttle.ui.screens.sources.FolderLists
import com.simplecityapps.shuttle.ui.screens.sources.MediaSources
import com.simplecityapps.shuttle.ui.screens.sources.ScannerFolderStore
import com.simplecityapps.shuttle.ui.screens.sources.SourceFolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeMediaSources(vararg enabled: MediaProviderType) : MediaSources {
    private val _enabledTypes = MutableStateFlow(enabled.toList())
    override val enabledTypes: StateFlow<List<MediaProviderType>> = _enabledTypes

    var scans = 0
        private set

    override fun enable(type: MediaProviderType) {
        if (type !in _enabledTypes.value) _enabledTypes.value += type
    }

    override fun disable(type: MediaProviderType) {
        _enabledTypes.value -= type
    }

    override fun scan() {
        scans++
    }
}

class FakeScannerFolderStore : ScannerFolderStore {
    private val _folders = MutableStateFlow(FolderLists())
    override val folders: StateFlow<FolderLists> = _folders

    /** Tree URIs [add] refuses, as it does for a folder outside this device's storage. */
    var refused = setOf<String>()

    override fun add(kind: FolderKind, treeUri: String): Boolean {
        if (treeUri in refused) return false
        val folder = SourceFolder(treeUri, "/storage/emulated/0/${treeUri.substringAfterLast('/')}", treeUri.substringAfterLast('/'))
        _folders.value = when (kind) {
            FolderKind.Include -> _folders.value.copy(includes = _folders.value.includes + folder)
            FolderKind.Exclude -> _folders.value.copy(excludes = _folders.value.excludes + folder)
            FolderKind.Extra -> _folders.value.copy(extras = _folders.value.extras + folder)
        }
        return true
    }

    override fun remove(kind: FolderKind, folder: SourceFolder) {
        _folders.value = FolderLists(_folders.value.includes - folder, _folders.value.excludes - folder, _folders.value.extras - folder)
    }

    override fun refresh() = Unit
}
