package com.simplecityapps.shuttle.ui.screens.playlistmenu

import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.model.Song
import java.io.Serializable

sealed class PlaylistData : Serializable {

    abstract fun getPlaylistSavedMessage(playlistName: String): String

    class Songs(val data: List<Song>) : PlaylistData() {

        constructor(song: Song) : this(listOf(song))

        override fun getPlaylistSavedMessage(playlistName: String): String {
            return "${data.size} song(s) added to $playlistName"
        }
    }

    class Albums(val data: List<Album>) : PlaylistData() {

        constructor(album: Album) : this(listOf(album))

        override fun getPlaylistSavedMessage(playlistName: String): String {
            return "${data.size} album(s) added to $playlistName"
        }
    }

    class AlbumArtists(val data: List<AlbumArtist>) : PlaylistData() {

        constructor(albumArtist: AlbumArtist) : this(listOf(albumArtist))

        override fun getPlaylistSavedMessage(playlistName: String): String {
            return "${data.size} artist(s) added to $playlistName"
        }
    }
}