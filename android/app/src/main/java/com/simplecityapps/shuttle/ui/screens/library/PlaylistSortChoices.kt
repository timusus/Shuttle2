package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.sorting.PlaylistKind
import com.simplecityapps.shuttle.sorting.SongListSort

/** [this] kind's sorts in menu order; its playlist order is labelled by what that order means for the kind. */
internal val PlaylistKind.sortChoices: List<SortChoice<SongListSort>>
    get() = sorts.map { sort -> sort.choice(this) }

private fun SongListSort.choice(kind: PlaylistKind): SortChoice<SongListSort> = when (this) {
    SongListSort.AlbumNewestAdded -> SortChoice(this, R.string.playlist_sort_album_recently_added, R.string.playlist_sort_short_album_recently_added)

    SongListSort.AlbumNewest -> SortChoice(this, R.string.artist_sort_album_newest, R.string.artist_sort_short_album_newest)

    SongListSort.AlbumOldest -> SortChoice(this, R.string.artist_sort_album_oldest, R.string.artist_sort_short_album_oldest)

    SongListSort.AlbumTitle -> SortChoice(this, R.string.artist_sort_album_title, R.string.artist_sort_short_album_title)

    SongListSort.AlbumRanked -> SortChoice(this, R.string.playlist_sort_album_ranked, R.string.playlist_sort_short_album_ranked)

    SongListSort.PlaylistOrder -> when (kind) {
        PlaylistKind.User -> SortChoice(this, R.string.menu_title_sort_custom)
        PlaylistKind.Favourites -> SortChoice(this, R.string.playlist_sort_recently_favourited, R.string.playlist_sort_short_recent)
        PlaylistKind.History -> SortChoice(this, R.string.playlist_sort_recently_played, R.string.playlist_sort_short_recent)
        else -> SortChoice(this, R.string.playlist_sort_playlist_order)
    }

    SongListSort.NewestAdded -> SortChoice(this, R.string.playlist_sort_recently_added, R.string.playlist_sort_short_recent)

    SongListSort.SongTitle -> SortChoice(this, R.string.artist_sort_song_title, R.string.artist_sort_short_song_title)

    SongListSort.Artist -> SortChoice(this, R.string.playlist_sort_artist, R.string.playlist_sort_short_artist)

    SongListSort.MostPlayed -> SortChoice(this, R.string.artist_sort_most_played)
}
