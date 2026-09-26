package com.simplecityapps.shuttle.ui.screens.playlistmenu

import android.content.res.Resources
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.MediaSelection

sealed class PlaylistData {
    abstract fun getPlaylistSavedMessage(
        resources: Resources,
        playlistName: String
    ): String

    class Songs(val data: List<com.simplecityapps.shuttle.model.Song>) : PlaylistData() {
        constructor(song: com.simplecityapps.shuttle.model.Song) : this(listOf(song))

        override fun getPlaylistSavedMessage(
            resources: Resources,
            playlistName: String
        ): String = resources.getQuantityString(R.plurals.playlist_songs_added, data.size, data.size, playlistName)
    }

    class Albums(val data: List<com.simplecityapps.shuttle.model.Album>) : PlaylistData() {
        constructor(album: com.simplecityapps.shuttle.model.Album) : this(listOf(album))

        override fun getPlaylistSavedMessage(
            resources: Resources,
            playlistName: String
        ): String = resources.getQuantityString(R.plurals.playlist_albums_added, data.size, data.size, playlistName)
    }

    class AlbumArtists(val data: List<com.simplecityapps.shuttle.model.AlbumArtist>) : PlaylistData() {
        constructor(albumArtist: com.simplecityapps.shuttle.model.AlbumArtist) : this(listOf(albumArtist))

        override fun getPlaylistSavedMessage(
            resources: Resources,
            playlistName: String
        ): String = resources.getQuantityString(R.plurals.playlist_artists_added, data.size, data.size, playlistName)
    }

    class Genres(val data: List<com.simplecityapps.shuttle.model.Genre>) : PlaylistData() {
        constructor(genre: com.simplecityapps.shuttle.model.Genre) : this(listOf(genre))

        override fun getPlaylistSavedMessage(
            resources: Resources,
            playlistName: String
        ): String = resources.getQuantityString(R.plurals.playlist_genres_added, data.size, data.size, playlistName)
    }

    /** Local library folders, each identified by its [com.simplecityapps.shuttle.model.SongFolder] path. */
    class Folders(val data: List<List<String>>) : PlaylistData() {
        override fun getPlaylistSavedMessage(
            resources: Resources,
            playlistName: String
        ): String = resources.getQuantityString(R.plurals.playlist_folders_added, data.size, data.size, playlistName)
    }

    object Queue : PlaylistData() {
        override fun getPlaylistSavedMessage(
            resources: Resources,
            playlistName: String
        ): String = resources.getString(R.string.playlist_queue_added, playlistName)
    }
}

/** The selection as a [MediaSelection], so the playlist menu shares the [com.simplecityapps.shuttle.ui.actions] use cases. */
fun PlaylistData.toMediaSelection(): MediaSelection = when (this) {
    is PlaylistData.Songs -> MediaSelection.Songs(data)
    is PlaylistData.Albums -> MediaSelection.Albums(data)
    is PlaylistData.AlbumArtists -> MediaSelection.AlbumArtists(data)
    is PlaylistData.Genres -> MediaSelection.Genres(data)
    is PlaylistData.Folders -> MediaSelection.Folders(data)
    is PlaylistData.Queue -> MediaSelection.Queue
}
