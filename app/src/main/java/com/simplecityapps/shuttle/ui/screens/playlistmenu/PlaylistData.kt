package com.simplecityapps.shuttle.ui.screens.playlistmenu

import android.content.res.Resources
import android.os.Parcelable
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.model.Genre
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import com.squareup.phrase.Phrase
import kotlinx.parcelize.Parcelize

sealed class PlaylistData : Parcelable {

    abstract fun getPlaylistSavedMessage(resources: Resources, playlistName: String): String

    @Parcelize
    class Songs(val data: List<Song>) : PlaylistData() {

        constructor(song: Song) : this(listOf(song))

        override fun getPlaylistSavedMessage(resources: Resources, playlistName: String): String {
            return Phrase.fromPlural(resources, R.plurals.playlist_songs_added, data.size)
                .put("count", data.size)
                .put("playlist_name", playlistName)
                .format()
                .toString()
        }
    }

    @Parcelize
    class Albums(val data: List<Album>) : PlaylistData() {

        constructor(album: Album) : this(listOf(album))

        override fun getPlaylistSavedMessage(resources: Resources, playlistName: String): String {
            return Phrase.fromPlural(resources, R.plurals.playlist_albums_added, data.size)
                .put("count", data.size)
                .put("playlist_name", playlistName)
                .format()
                .toString()
        }
    }

    @Parcelize
    class AlbumArtists(val data: List<AlbumArtist>) : PlaylistData() {

        constructor(albumArtist: AlbumArtist) : this(listOf(albumArtist))

        override fun getPlaylistSavedMessage(resources: Resources, playlistName: String): String {
            return Phrase.fromPlural(resources, R.plurals.playlist_artists_added, data.size)
                .put("count", data.size)
                .put("playlist_name", playlistName)
                .format()
                .toString()
        }
    }

    @Parcelize
    class Genres(val data: List<Genre>) : PlaylistData() {

        constructor(genre: Genre) : this(listOf(genre))

        override fun getPlaylistSavedMessage(resources: Resources, playlistName: String): String {
            return Phrase.fromPlural(resources, R.plurals.playlist_genres_added, data.size)
                .put("count", data.size)
                .put("playlist_name", playlistName)
                .format()
                .toString()
        }
    }

    @Parcelize
    object Queue : PlaylistData() {

        override fun getPlaylistSavedMessage(resources: Resources, playlistName: String): String {
            return Phrase.from(resources, R.string.playlist_queue_added)
                .put("playlist_name", playlistName)
                .format()
                .toString()
        }
    }
}