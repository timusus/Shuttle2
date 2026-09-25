package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.shuttle.downloads.SongDownload
import com.simplecityapps.shuttle.downloads.SongDownloadRepository
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The actions the shared actions sheet offers for a selection, in display order, updating as downloads change.
 *
 * - Play, Shuffle, Play next, Add to queue, Add to playlist: everything but the queue, which only offers Add to
 *   playlist (save the queue).
 * - Go to album: a single song. Go to artist: a single song or album.
 * - Edit tags: when every provider in the selection supports tag editing (local files only).
 * - Song info: a single song.
 * - Share: songs and albums.
 * - Exclude: songs, albums, artists and genres; it runs straight away with an Undo (redesign owner decision 11).
 * - Delete: songs, when every one can be deleted (a local file, not a remote provider's).
 * - Download: when any of the selection's remote songs isn't downloaded or downloading. Remove download: when any is.
 *   Local songs never offer either.
 */
class AvailableMediaActions @Inject constructor(
    private val resolveSongs: ResolveSongs,
    private val songDownloadRepository: SongDownloadRepository,
) {
    operator fun invoke(selection: MediaSelection): Flow<List<MediaActionType>> {
        val base = baseActions(selection)
        if (selection.mediaProviders?.any { it.remote } != true) return flowOf(base)
        return flow {
            val songs = resolveSongs(selection)
            emitAll(songDownloadRepository.observeDownloads().map { downloads -> base + downloadActions(songs, downloads) })
        }
    }

    private fun baseActions(selection: MediaSelection): List<MediaActionType> = buildList {
        if (selection is MediaSelection.Queue) {
            add(MediaActionType.AddToPlaylist)
            return@buildList
        }
        addAll(listOf(MediaActionType.Play, MediaActionType.Shuffle, MediaActionType.PlayNext, MediaActionType.AddToQueue, MediaActionType.AddToPlaylist))

        val singleSong = (selection as? MediaSelection.Songs)?.songs?.singleOrNull()
        if (singleSong != null) add(MediaActionType.GoToAlbum)
        if (singleSong != null || (selection as? MediaSelection.Albums)?.albums?.size == 1) add(MediaActionType.GoToArtist)

        val providers = selection.mediaProviders
        if (!providers.isNullOrEmpty() && providers.all { it.supportsTagEditing }) add(MediaActionType.EditTags)

        if (singleSong != null) add(MediaActionType.SongInfo)
        if (selection is MediaSelection.Songs || selection is MediaSelection.Albums) add(MediaActionType.Share)

        when (selection) {
            is MediaSelection.Songs, is MediaSelection.Albums, is MediaSelection.AlbumArtists, is MediaSelection.Genres ->
                add(MediaActionType.Exclude)

            else -> Unit
        }

        if (selection is MediaSelection.Songs && selection.songs.isNotEmpty() && selection.songs.all { it.isDeletable }) {
            add(MediaActionType.Delete)
        }
    }

    private fun downloadActions(songs: List<Song>, downloads: List<SongDownload>): List<MediaActionType> {
        val remotePaths = songs.filter { it.mediaProvider.remote }.map { it.path }
        if (remotePaths.isEmpty()) return emptyList()
        val downloaded = downloads.filter { it.state in HELD_STATES }.mapTo(mutableSetOf()) { it.path }
        return buildList {
            if (remotePaths.any { it !in downloaded }) add(MediaActionType.Download)
            if (remotePaths.any { it in downloaded }) add(MediaActionType.RemoveDownload)
        }
    }

    private val Song.isDeletable: Boolean
        get() = canBeDeleted() && !mediaProvider.remote

    private companion object {
        /** A download in any of these states is on the device or on its way; Failed and Removing aren't. */
        val HELD_STATES = setOf(
            SongDownload.State.Queued,
            SongDownload.State.Downloading,
            SongDownload.State.Completed,
            SongDownload.State.Stopped,
        )
    }
}
