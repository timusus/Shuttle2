package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.PlaylistKind
import com.simplecityapps.shuttle.sorting.SongListSort
import com.simplecityapps.shuttle.sorting.SongSection
import com.simplecityapps.shuttle.sorting.albumsOf
import com.simplecityapps.shuttle.sorting.songSections
import com.simplecityapps.shuttle.ui.actions.ShuffleAlbums
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach

/**
 * The song-list body of a detail screen: the sort chosen from [kind]'s set and saved under [key] (a
 * [com.simplecityapps.shuttle.sorting.PlaylistSortKey]), the album sections that sort yields, which of them are
 * unfolded, and shuffle albums. [T] lets a screen list entries that wrap a song (a playlist's entries) while
 * sections and play order stay consistent with the same emission.
 */
class SongCollection<T>(
    private val items: Flow<List<T>>,
    private val songOf: (T) -> Song,
    val kind: PlaylistKind,
    key: String,
    readSetting: ReadLibraryViewSetting,
    private val saveSetting: SaveLibraryViewSetting,
    private val shuffleAlbums: ShuffleAlbums,
) {
    class View<T>(
        val sections: List<SongSection>,
        /** Every item in [sort]'s order across all [sections], collapsed ones included: the play order. */
        val items: List<T>,
        val sort: SongListSort,
        val expandedAlbums: Set<AlbumGroupKey>,
    )

    private val setting = LibraryViewSetting.PlaylistDetailSort(key)
    private val selected = MutableStateFlow(kind.resolve(readSetting(setting)))
    private val expansion = AlbumExpansion()

    val view: Flow<View<T>> = combine(
        items.onEach { if (it.isNotEmpty()) expansion.onLoaded(albumsOf(it.map(songOf))) },
        selected,
        expansion.flow,
    ) { items, sort, expanded ->
        val sections = songSections(items.map(songOf), sort)
        View(sections, inSectionOrder(items, sections), sort, sections.expandedAmong(expanded))
    }

    fun select(sort: SongListSort) {
        if (sort !in kind.sorts) return
        selected.value = sort
        saveSetting(setting, sort)
    }

    fun toggleAlbum(album: Album, sections: List<SongSection>) = expansion.toggle(album, sections.albums())

    fun expandAll(sections: List<SongSection>) = expansion.expandAll(sections.albums())

    fun collapseAll() = expansion.collapseAll()

    /** Null when playback started or [songs] is empty. */
    suspend fun shuffle(songs: List<Song>): ShuffleAlbums.Result.Failure? {
        if (songs.isEmpty()) return null
        return shuffleAlbums(songs) as? ShuffleAlbums.Result.Failure
    }

    private fun List<SongSection>.albums() = mapNotNull { it.album }

    /** [items] in the order [sections] list their songs; a song listed twice keeps its items apart. */
    private fun inSectionOrder(items: List<T>, sections: List<SongSection>): List<T> {
        val bySong = items.groupBy { songOf(it).id }.mapValues { ArrayDeque(it.value) }
        return sections.flatMap { it.songs }.map { bySong.getValue(it.id).removeFirst() }
    }
}

/** The albums of [this] sections' that [expanded] holds, dropping keys of albums no longer listed. */
fun List<SongSection>.expandedAmong(expanded: Set<AlbumGroupKey>): Set<AlbumGroupKey> = mapNotNullTo(HashSet()) { it.album?.groupKey }.intersect(expanded)

/** Whether every album section is unfolded; false with none, which have no Expand all toggle. */
fun List<SongSection>.allAlbumsExpanded(expanded: Set<AlbumGroupKey>): Boolean = mapNotNull { it.album }.let { albums ->
    albums.isNotEmpty() && albums.all { album -> album.groupKey?.let { it in expanded } ?: false }
}
