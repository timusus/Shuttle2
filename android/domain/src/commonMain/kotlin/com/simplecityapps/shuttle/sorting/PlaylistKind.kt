package com.simplecityapps.shuttle.sorting

import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.sorting.SongListSort.AlbumNewest
import com.simplecityapps.shuttle.sorting.SongListSort.AlbumNewestAdded
import com.simplecityapps.shuttle.sorting.SongListSort.AlbumTitle
import com.simplecityapps.shuttle.sorting.SongListSort.Artist
import com.simplecityapps.shuttle.sorting.SongListSort.NewestAdded
import com.simplecityapps.shuttle.sorting.SongListSort.PlaylistOrder
import com.simplecityapps.shuttle.sorting.SongListSort.SongTitle

private val albumSorts = listOf(AlbumNewestAdded, AlbumTitle, AlbumNewest)
private val everySongSort = listOf(PlaylistOrder, NewestAdded, SongTitle, Artist, SongListSort.MostPlayed)

/**
 * The view options a playlist detail screen offers: the [sorts] it lists, in menu order, and the one it opens
 * in. [PlaylistOrder] is the songs' own order: a regular playlist's stored order (the only one it can be
 * reordered in), or the smart query's sort.
 */
enum class PlaylistKind(val defaultSort: SongListSort, val sorts: List<SongListSort>) {
    RecentlyAdded(AlbumNewestAdded, albumSorts + listOf(NewestAdded, SongTitle, Artist)),
    Downloaded(AlbumTitle, albumSorts + listOf(NewestAdded, SongTitle, Artist)),

    /** Ordered by when each song was favourited, newest first. */
    Favourites(PlaylistOrder, listOf(PlaylistOrder, SongTitle, Artist) + albumSorts),
    MostPlayed(SongListSort.MostPlayed, listOf(SongListSort.MostPlayed, SongTitle, Artist)),

    /** Ordered by when each song last played to the end, newest first. */
    History(PlaylistOrder, listOf(PlaylistOrder, SongTitle, Artist)),
    UserSmart(PlaylistOrder, everySongSort + albumSorts),
    User(PlaylistOrder, everySongSort + albumSorts),
    ;

    val offersGrouping: Boolean get() = sorts.any { it.groupsByAlbum }

    /** [saved] while this kind still offers it, else [defaultSort]. */
    fun resolve(saved: SongListSort?): SongListSort = saved?.takeIf { it in sorts } ?: defaultSort

    companion object {
        fun of(id: SmartPlaylistId): PlaylistKind = when (id) {
            SmartPlaylistId.Favourites -> Favourites
            SmartPlaylistId.RecentlyAdded -> RecentlyAdded
            SmartPlaylistId.MostPlayed -> MostPlayed
            SmartPlaylistId.History -> History
            SmartPlaylistId.Downloaded -> Downloaded
        }
    }
}

/** The preference keys a playlist's chosen [SongListSort] is saved under, one per playlist. */
object PlaylistSortKey {
    fun of(id: SmartPlaylistId): String = "playlist_sort_${id.id}"

    fun user(playlistId: Long): String = "playlist_sort_user_$playlistId"

    fun userSmart(smartPlaylistId: Long): String = "playlist_sort_smart_$smartPlaylistId"
}
