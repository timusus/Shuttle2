package com.simplecityapps.shuttle.ui.screens.library.folders

import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.model.Song

fun createFolder(
    vararg path: String = arrayOf("primary", "Music"),
    songCount: Int = 1,
) = Folder(path = path.toList(), songCount = songCount)

fun readyFolderList(
    currentFolder: Folder? = null,
    folders: List<Folder> = listOf(createFolder()),
    songs: List<Song> = emptyList(),
    // One storage volume, so its level is skipped: crumbs start below the first path segment
    breadcrumbs: List<Folder> = currentFolder?.let { current -> (2..current.path.size).map { createFolder(*current.path.take(it).toTypedArray()) } }.orEmpty(),
) = FolderListUiState(
    currentFolder = currentFolder,
    breadcrumbs = breadcrumbs,
    folders = folders,
    songs = songs,
    loadingState = FolderListUiState.LoadingState.Ready,
)

fun scanningFolderList(progress: Progress? = null) = FolderListUiState(
    loadingState = FolderListUiState.LoadingState.Scanning,
    scanProgress = progress,
)

fun emptyFolderList() = FolderListUiState(
    loadingState = FolderListUiState.LoadingState.Empty,
)

val loadingFolderList = FolderListUiState(
    loadingState = FolderListUiState.LoadingState.Loading,
)
