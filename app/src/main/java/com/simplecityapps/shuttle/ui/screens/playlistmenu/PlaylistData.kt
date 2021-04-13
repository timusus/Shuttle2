package com.simplecityapps.shuttle.ui.screens.playlistmenu

import android.content.res.Resources
import android.os.Parcelable
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.model.Genre
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import kotlinx.parcelize.Parcelize

sealed class PlaylistData : Parcelable {

    abstract fun getPlaylistSavedMessage(resources: Resources, playlistName: String): String

    @Parcelize
    class Songs(val data: List<Song>) : PlaylistData() {

        constructor(song: Song) : this(listOf(song))

        override fun getPlaylistSavedMessage(resources: Resources, playlistName: String): String {
            return "${resources.getQuantityString(R.plurals.songsPlural, data.size, data.size)} added to $playlistName"
        }
    }

    @Parcelize
    class Albums(val data: List<Album>) : PlaylistData() {

        constructor(album: Album) : this(listOf(album))

        override fun getPlaylistSavedMessage(resources: Resources, playlistName: String): String {
            return "${resources.getQuantityString(R.plurals.albumsPlural, data.size, data.size)} added to $playlistName"
        }
    }

    @Parcelize
    class AlbumArtists(val data: List<AlbumArtist>) : PlaylistData() {

        constructor(albumArtist: AlbumArtist) : this(listOf(albumArtist))

        override fun getPlaylistSavedMessage(resources: Resources, playlistName: String): String {
            return "${resources.getQuantityString(R.plurals.artistsPlural, data.size, data.size)} added to $playlistName"
        }
    }

    @Parcelize
    class Genres(val data: List<Genre>) : PlaylistData() {

        constructor(genre: Genre) : this(listOf(genre))

        override fun getPlaylistSavedMessage(resources: Resources, playlistName: String): String {
            return "${resources.getQuantityString(R.plurals.genresPlural, data.size, data.size)} added to $playlistName"
        }
    }

    @Parcelize
    object Queue : PlaylistData() {

        override fun getPlaylistSavedMessage(resources: Resources, playlistName: String): String {
            return "Queue added to $playlistName"
        }
    }
}