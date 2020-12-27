package com.simplecityapps.shuttle.ui.screens.library.albums

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionViewBinder
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes

class ListAlbumBinder(
    album: Album,
    imageLoader: ArtworkImageLoader,
    listener: Listener
) : AlbumBinder(album, imageLoader, listener),
    SectionViewBinder {

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_album, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.AlbumList
    }

    class ViewHolder(itemView: View) : AlbumBinder.ViewHolder(itemView) {

        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        override val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val overflowButton: ImageButton = itemView.findViewById(R.id.overflowButton)
        private val checkImageView: ImageView = itemView.findViewById(R.id.checkImageView)

        init {
            itemView.setOnClickListener { viewBinder?.listener?.onAlbumClicked(viewBinder!!.album, this) }
            itemView.setOnLongClickListener {
                viewBinder?.listener?.onAlbumLongClicked(viewBinder!!.album, this)
                true
            }
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

            checkImageView.isVisible = viewBinder.selected
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
        }
    }
}