package com.simplecityapps.shuttle.ui.screens.library.albumartists

import android.view.View
import android.widget.ImageView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionViewBinder
import com.simplecityapps.shuttle.ui.screens.home.search.ArtistJaroSimilarity

abstract class AlbumArtistBinder(
    val albumArtist: AlbumArtist,
    val imageLoader: ArtworkImageLoader,
    val listener: Listener,
    val jaroSimilarity: ArtistJaroSimilarity? = null
) : ViewBinder,
    SectionViewBinder {

    var selected: Boolean = false

    interface Listener {
        fun onAlbumArtistClicked(albumArtist: AlbumArtist, viewHolder: ViewHolder)
        fun onOverflowClicked(view: View, albumArtist: AlbumArtist) {}
        fun onAlbumArtistLongClicked(view: View, albumArtist: AlbumArtist)
        fun onViewHolderCreated(holder: ViewHolder) {}
    }

    override fun getSectionName(): String? {
        return albumArtist.groupKey?.key?.firstOrNull()?.toUpperCase()?.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AlbumArtistBinder) return false

        if (albumArtist != other.albumArtist) return false

        return true
    }

    override fun hashCode(): Int {
        return albumArtist.hashCode()
    }

    override fun areContentsTheSame(other: Any): Boolean {
        if (other !is AlbumArtistBinder) return false

        return albumArtist.name == other.albumArtist.name
                && albumArtist.artists == other.albumArtist.artists
                && albumArtist.albumCount == other.albumArtist.albumCount
                && albumArtist.songCount == other.albumArtist.songCount
                && selected == other.selected
                && jaroSimilarity == other.jaroSimilarity
    }


    abstract class ViewHolder(itemView: View) : ViewBinder.ViewHolder<AlbumArtistBinder>(itemView) {
        abstract val imageView: ImageView
    }
}