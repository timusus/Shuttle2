package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.sorting.PlaylistKind
import com.simplecityapps.shuttle.sorting.SongListSort
import com.simplecityapps.shuttle.sorting.SongSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A playlist detail screen's view options: the sort chosen from [kind]'s set, saved under [key] (a
 * [com.simplecityapps.shuttle.sorting.PlaylistSortKey]), and which album sections are unfolded.
 */
class PlaylistViewOptions(
    val kind: PlaylistKind,
    key: String,
    readSetting: ReadLibraryViewSetting,
    private val saveSetting: SaveLibraryViewSetting,
) {
    private val setting = LibraryViewSetting.PlaylistDetailSort(key)
    private val selected = MutableStateFlow(kind.resolve(readSetting(setting)))

    val sort: StateFlow<SongListSort> = selected.asStateFlow()
    val expansion = AlbumExpansion()

    fun select(sort: SongListSort) {
        if (sort !in kind.sorts) return
        selected.value = sort
        saveSetting(setting, sort)
    }
}

/** The albums of [this] sections' that [expanded] holds, dropping keys of albums no longer listed. */
fun List<SongSection>.expandedAmong(expanded: Set<AlbumGroupKey>): Set<AlbumGroupKey> = mapNotNullTo(HashSet()) { it.album?.groupKey }.intersect(expanded)

/** Whether every album section is unfolded; false with none, which have no Expand all toggle. */
fun List<SongSection>.allAlbumsExpanded(expanded: Set<AlbumGroupKey>): Boolean = mapNotNull { it.album }.let { albums ->
    albums.isNotEmpty() && albums.all { album -> album.groupKey?.let { it in expanded } ?: false }
}
