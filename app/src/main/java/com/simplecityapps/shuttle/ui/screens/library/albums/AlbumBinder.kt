package com.simplecityapps.shuttle.ui.screens.library.albums

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.shuttle.R

class AlbumBinder(val album: Album) : ViewBinder {

    interface Listener {
        fun onAlbumClicked(album: Album)
    }

    var listener: Listener? = null

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_album, parent, false))
    }

    override fun viewType(): ViewBinder.ViewType {
        return ViewBinder.ViewType.Album
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
            itemView.setOnClickListener { viewBinder?.listener?.onAlbumClicked(viewBinder!!.album) }
        }

        private val title = itemView.findViewById<TextView>(R.id.title)
        private val subtitle = itemView.findViewById<TextView>(R.id.subtitle)
        private val imageView = itemView.findViewById<ImageView>(R.id.imageView)

        override fun bind(viewBinder: AlbumBinder) {
            super.bind(viewBinder)

            title.text = viewBinder.album.name
            subtitle.text = viewBinder.album.toString()
        }
    }
}