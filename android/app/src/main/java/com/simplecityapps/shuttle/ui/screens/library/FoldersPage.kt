package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.FolderEntryKind
import com.simplecityapps.shuttle.designsystem.component.FolderRow
import com.simplecityapps.shuttle.designsystem.component.S2Breadcrumb
import com.simplecityapps.shuttle.designsystem.component.StateAction
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.screens.library.folders.Folder
import com.simplecityapps.shuttle.ui.screens.library.folders.FolderListUiState
import com.simplecityapps.shuttle.ui.screens.library.folders.displayName
import com.simplecityapps.shuttle.ui.shell.ScrollToTopOnReselect
import com.simplecityapps.shuttle.ui.shell.ShellTab

/** Folders: the current folder's subfolders, then its songs; a breadcrumb path jumps back to any ancestor. */
@Composable
fun FoldersPage(
    state: FolderListUiState,
    onFolderClick: (Folder) -> Unit,
    onFolderMore: (Folder) -> Unit,
    onSongClick: (Song) -> Unit,
    onSongMore: (Song) -> Unit,
    onRootClick: () -> Unit,
    onAddFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = when (state.loadingState) {
        FolderListUiState.LoadingState.Loading -> LibraryContentState.Loading
        FolderListUiState.LoadingState.Scanning -> LibraryContentState.Scanning
        FolderListUiState.LoadingState.Empty -> LibraryContentState.Empty
        FolderListUiState.LoadingState.Ready -> LibraryContentState.Ready
    }
    LibraryContent(
        content,
        stringResource(R.string.folder_list_empty),
        modifier,
        state.scanProgress,
        emptyMessage = stringResource(R.string.folder_list_empty_message),
        emptyAction = StateAction(stringResource(R.string.sources_add_folder), onAddFolder),
    ) {
        val listState = rememberLazyListState()
        ScrollToTopOnReselect(ShellTab.Library, listState)
        LazyColumn(state = listState, modifier = modifier.fillMaxSize().testTag("library-folders")) {
            if (state.breadcrumbs.isNotEmpty()) {
                item(key = "path") {
                    val rootLabel = stringResource(R.string.library_tab_folders)
                    val resources = LocalContext.current.resources
                    S2Breadcrumb(
                        segments = listOf(rootLabel) + state.breadcrumbs.map { it.displayName(resources) },
                        onSegmentClick = { index -> if (index == 0) onRootClick() else onFolderClick(state.breadcrumbs[index - 1]) },
                    )
                }
            }
            items(state.folders, key = { "folder-" + it.path.joinToString("/") }) { folder ->
                FolderRow(
                    name = folder.displayName(),
                    kind = FolderEntryKind.Folder,
                    summary = pluralString(R.plurals.songsPlural, folder.songCount),
                    onClick = { onFolderClick(folder) },
                    onMore = { onFolderMore(folder) },
                )
            }
            items(state.songs, key = { "song-${it.id}" }) { song ->
                LibrarySongRow(song = song, onClick = { onSongClick(song) }, onMore = { onSongMore(song) })
            }
        }
    }
}
