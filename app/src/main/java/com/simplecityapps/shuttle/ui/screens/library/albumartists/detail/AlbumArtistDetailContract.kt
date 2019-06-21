package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract

class AlbumArtistDetailContract {

    interface View {
        fun setListData(albums: Map<Album, List<Song>>)
        fun showLoadError(error: Error)
        fun onAddedToQueue(name: String)
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun loadData()
        fun onSongClicked(song: Song, songs: List<Song>)
        fun shuffle()
        fun addToQueue(albumArtist: AlbumArtist)
        fun addToQueue(album: Album)
        fun addToQueue(song: Song)
    }
}