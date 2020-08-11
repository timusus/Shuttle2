package com.simplecityapps.shuttle.ui.screens.library.albums

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionViewBinder
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes

class AlbumBinder(
    val album: Album,
    val imageLoader: ArtworkImageLoader,
    val listener: Listener? = null
) : ViewBinder,
    SectionViewBinder {

    interface Listener {
        fun onAlbumClicked(album: Album, viewHolder: ViewHolder)
        fun onOverflowClicked(view: View, album: Album) {}
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_album, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.Album
    }

    override fun getSectionName(): String? {
        return album.sortKey?.firstOrNull().toString()
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

        return album.name == other.album.name
                && album.albumArtist == other.album.albumArtist
                && album.songCount == other.album.songCount
                && album.year == other.album.year
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<AlbumBinder>(itemView) {

        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val overflowButton: ImageButton = itemView.findViewById(R.id.overflowButton)

        init {
            itemView.setOnClickListener { viewBinder?.listener?.onAlbumClicked(viewBinder!!.album, this) }
            overflowButton.setOnClickListener {
                viewBinder?.listener?.onOverflowClicked(it, viewBinder!!.album)
            }
        }

        override fun bind(viewBinder: AlbumBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.album.name
            subtitle.text = "${viewBinder.album.albumArtist} • ${subtitle.resources.getQuantityString(R.plurals.songsPlural, viewBinder.album.songCount, viewBinder.album.songCount)}"

            viewBinder.imageLoader.loadArtwork(
                imageView, viewBinder.album,
                ArtworkImageLoader.Options.RoundedCorners(16),
                ArtworkImageLoader.Options.Crossfade(200)
            )

            imageView.transitionName = "album_${viewBinder.album.name}"
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
        }
    }
}