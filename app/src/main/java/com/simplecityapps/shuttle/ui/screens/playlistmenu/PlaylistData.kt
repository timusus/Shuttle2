package com.simplecityapps.shuttle.ui.screens.playlistmenu

import android.content.res.Resources
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.model.Genre
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import java.io.Serializable

sealed class PlaylistData : Serializable {

    abstract fun getPlaylistSavedMessage(resources: Resources, playlistName: String): String

    class Songs(val data: List<Song>) : PlaylistData() {

        constructor(song: Song) : this(listOf(song))

        override fun getPlaylistSavedMessage(resources: Resources, playlistName: String): String {
            return "${resources.getQuantityString(R.plurals.songsPlural, data.size, data.size)} added to $playlistName"
        }
    }

    class Albums(val data: List<Album>) : PlaylistData() {

        constructor(album: Album) : this(listOf(album))

        override fun getPlaylistSavedMessage(resources: Resources, playlistName: String): String {
            return "${resources.getQuantityString(R.plurals.albumsPlural, data.size, data.size)} added to $playlistName"
        }
    }

    class AlbumArtists(val data: List<AlbumArtist>) : PlaylistData() {

        constructor(albumArtist: AlbumArtist) : this(listOf(albumArtist))

        override fun getPlaylistSavedMessage(resources: Resources, playlistName: String): String {
            return "${resources.getQuantityString(R.plurals.artistsPlural, data.size, data.size)} added to $playlistName"
        }
    }

    class Genres(val data: List<Genre>) : PlaylistData() {

        constructor(genre: Genre) : this(listOf(genre))

        override fun getPlaylistSavedMessage(resources: Resources, playlistName: String): String {
            return "${resources.getQuantityString(R.plurals.genresPlural, data.size, data.size)} added to $playlistName"
        }
    }

    object Queue : PlaylistData() {

        override fun getPlaylistSavedMessage(resources: Resources, playlistName: String): String {
            return "Queue added to $playlistName"
        }
    }
}