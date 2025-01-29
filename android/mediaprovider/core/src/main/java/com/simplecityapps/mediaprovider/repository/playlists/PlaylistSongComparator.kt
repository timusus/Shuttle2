package com.simplecityapps.mediaprovider.repository.playlists

import com.simplecityapps.mediaprovider.repository.songs.SongComparator
import com.simplecityapps.shuttle.model.PlaylistSong
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder

val PlaylistSongSortOrder.comparator: Comparator<PlaylistSong>
    get() {
        return when (this) {
            PlaylistSongSortOrder.Position -> PlaylistSongComparator.positionComparator
            PlaylistSongSortOrder.SongName -> DelegatingComparator(SongComparator.songNameComparator)
            PlaylistSongSortOrder.ArtistGroupKey -> DelegatingComparator(SongComparator.artistGroupKeyComparator)
            PlaylistSongSortOrder.AlbumGroupKey -> DelegatingComparator(SongComparator.albumGroupKeyComparator)
            PlaylistSongSortOrder.Year -> DelegatingComparator(SongComparator.yearComparator)
            PlaylistSongSortOrder.Duration -> DelegatingComparator(SongComparator.durationComparator)
            PlaylistSongSortOrder.Track -> DelegatingComparator(SongComparator.trackComparator)
            PlaylistSongSortOrder.PlayCount -> DelegatingComparator(SongComparator.playCountComparator)
            PlaylistSongSortOrder.LastModified -> DelegatingComparator(SongComparator.lastModifiedComparator)
            PlaylistSongSortOrder.LastCompleted -> DelegatingComparator(SongComparator.lastCompletedComparator)
        }
    }

object PlaylistSongComparator {
    val positionComparator: Comparator<PlaylistSong> by lazy {
        compareBy { it.sortOrder }
    }
}

class DelegatingComparator(private val songComparator: Comparator<Song>) : Comparator<PlaylistSong> {
    override fun compare(
        o1: PlaylistSong,
        o2: PlaylistSong
    ): Int = songComparator.compare(o1.song, o2.song)
}
