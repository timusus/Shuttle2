package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumItem
import com.simplecityapps.shuttle.model.FindOtherRecordings
import com.simplecityapps.shuttle.model.OtherRecording
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.groupWorks
import com.simplecityapps.shuttle.model.isClassicalBlock
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

/** The rows with the songs they were built from: row play indices point into these, so the screen must play from this list. */
internal class Grouped(val songs: List<Song>, val rows: AlbumRows?, val otherRecordings: Map<Long, List<OtherRecording>>)

internal class ClassicalRows(
    songs: Flow<List<Song>>,
    album: Flow<Album?>,
    classicalDisplay: Flow<Boolean>,
    observeSongs: ObserveSongs,
    findOtherRecordings: FindOtherRecordings,
    ioDispatcher: CoroutineDispatcher,
) {
    /** Works (by first song id) whose collapse state the user flipped from its default. */
    private val toggledWorks = MutableStateFlow<Set<Long>>(emptySet())

    fun toggleWork(firstSongId: Long) {
        toggledWorks.update { if (firstSongId in it) it - firstSongId else it + firstSongId }
    }

    /**
     * Found once per change to the album's works or the library, off the main thread, never per row. Starts empty so the
     * Ready state never waits on the library scan. The library is only read while classical display is on and the album
     * has a strictly classical work, so a pop library is never loaded for its albums.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val otherRecordings: Flow<Map<Long, List<OtherRecording>>> = combine(songs, classicalDisplay.distinctUntilChanged()) { albumSongs, classical ->
        if (classical) groupWorks(albumSongs).filterIsInstance<AlbumItem.Work>().map { it.block }.filter { it.songs.isClassicalBlock() } else emptyList()
    }
        .distinctUntilChanged()
        .flatMapLatest { works ->
            if (works.isEmpty()) {
                flowOf(emptyMap())
            } else {
                observeSongs().map { library -> findOtherRecordings(works, library) }.flowOn(ioDispatcher)
            }
        }
        .distinctUntilChanged()
        .onStart { emit(emptyMap()) }

    /** The grouping, worked out once per change to the songs, album or settings; a song change in the player leaves it alone. */
    val grouped: Flow<Grouped> = combine(songs, album, classicalDisplay, toggledWorks, otherRecordings) { songs, album, classical, toggled, otherRecordings ->
        Grouped(songs, if (classical) buildAlbumRows(songs, album?.albumArtist ?: album?.friendlyArtistName, null, toggled, otherRecordings) else null, otherRecordings)
    }
}

internal fun AlbumRows.withPlaying(songId: Long?): AlbumRows {
    if (songId == null) return this
    return copy(
        rows = rows.map { row ->
            when (row) {
                is AlbumRow.Track -> row.copy(playing = row.song.id == songId)
                is AlbumRow.Movement -> row.copy(playing = row.song.id == songId)
                is AlbumRow.WorkHeader -> row.copy(playing = row.songs.any { it.id == songId })
                else -> row
            }
        }
    )
}
