package com.simplecityapps.shuttle.ui.screens.library.albums

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes

class AlbumBinder(
    val album: Album,
    val imageLoader: ArtworkImageLoader
) : ViewBinder {

    interface Listener {
        fun onAlbumClicked(album: Album, viewHolder: ViewHolder)
    }

    var listener: Listener? = null

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_album, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.Album
    }

    override fun sectionName(): String? {
        return album.name.firstOrNull().toString()
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
        return album.name == (other as? AlbumBinder)?.album?.name
                && album.albumArtistName == (other as? AlbumBinder)?.album?.albumArtistName
    }

    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<AlbumBinder>(itemView) {

        init {
            itemView.setOnClickListener { viewBinder?.listener?.onAlbumClicked(viewBinder!!.album, this) }
        }

        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        val imageView: ImageView = itemView.findViewById(R.id.imageView)

        override fun bind(viewBinder: AlbumBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.album.name
            subtitle.text = "${viewBinder.album.albumArtistName} • ${viewBinder.album.songCount} Songs"

            viewBinder.imageLoader.loadArtwork(imageView, viewBinder.album, ArtworkImageLoader.Options.RoundedCorners(16), completionHandler = null)

            imageView.transitionName = "album_${viewBinder.album.name}"
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
        }
    }
}