package com.simplecityapps.shuttle.ui.screens.library.albums

import android.view.View
import android.widget.ImageView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionViewBinder
import java.util.*

abstract class AlbumBinder(
    val album: com.simplecityapps.shuttle.model.Album,
    val imageLoader: ArtworkImageLoader,
    val listener: Listener? = null
) : ViewBinder,
    SectionViewBinder {

    var selected: Boolean = false

    interface Listener {
        fun onAlbumClicked(album: com.simplecityapps.shuttle.model.Album, viewHolder: ViewHolder)
        fun onAlbumLongClicked(album: com.simplecityapps.shuttle.model.Album, viewHolder: ViewHolder)
        fun onOverflowClicked(view: View, album: com.simplecityapps.shuttle.model.Album) {}
        fun onViewHolderCreated(holder: ViewHolder) {}
    }

    override fun getSectionName(): String? {
        return album.groupKey?.key?.firstOrNull()?.toString()?.toUpperCase(Locale.getDefault())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AlbumBinder) return false

        if (album != other.album) return false

        return true
    }

    override fun hashCode(): Int {
        return album.hashCode()
    }

    override fun areContentsTheSame(other: Any): Boolean {
        if (other !is AlbumBinder) return false

        return album.name == other.album.name &&
            album.artists == other.album.artists &&
            album.albumArtist == other.album.albumArtist &&
            album.songCount == other.album.songCount &&
            album.year == other.album.year &&
            selected == other.selected
    }

    abstract class ViewHolder(itemView: View) : ViewBinder.ViewHolder<AlbumBinder>(itemView) {
        abstract val imageView: ImageView
    }
}
