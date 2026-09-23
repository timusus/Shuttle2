package com.simplecityapps.shuttle.ui.screens.library.folders

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.SongFolder.inFolderOrder
import com.simplecityapps.shuttle.query.SongQuery
import javax.inject.Inject
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull

/**
 * Resolves folders to the songs in them and all their subfolders, in the order the folder browser lists them.
 */
class ResolveFolderSongs @Inject constructor(
    private val songRepository: SongRepository,
) {
    suspend operator fun invoke(paths: List<List<String>>): List<Song> = paths
        .flatMap { path ->
            songRepository.getSongs(SongQuery.Folder(path))
                // The repository emits null until its cache has loaded
                .filterNotNull()
                .firstOrNull()
                .orEmpty()
                .inFolderOrder()
        }
        .distinct()
}
