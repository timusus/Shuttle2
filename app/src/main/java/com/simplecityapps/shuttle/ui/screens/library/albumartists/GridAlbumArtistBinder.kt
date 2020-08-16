package com.simplecityapps.shuttle.ui.screens.library.albumartists

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.common.utils.dp

class GridAlbumArtistBinder(
    albumArtist: AlbumArtist,
    imageLoader: ArtworkImageLoader,
    listener: Listener,
    val coloredBackground: Boolean = false,
    val fixedWidthDp: Int? = null
) : AlbumArtistBinder(albumArtist, imageLoader, listener) {

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.grid_item_album_artist, parent, false)
        )
    }

    override fun viewType(): Int {
        return ViewTypes.AlbumArtistGrid
    }

    class ViewHolder(itemView: View) : AlbumArtistBinder.ViewHolder(itemView) {

        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        override val imageView: ImageView = itemView.findViewById(R.id.imageView)

        init {
            itemView.setOnClickListener {
                viewBinder?.listener?.onAlbumArtistClicked(viewBinder!!.albumArtist, this)
            }
            itemView.setOnLongClickListener {
                viewBinder?.listener?.onOverflowClicked(itemView, viewBinder!!.albumArtist)
                true
            }
            subtitle.visibility = View.GONE
        }

        override fun bind(viewBinder: AlbumArtistBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.albumArtist.name

            viewBinder.imageLoader.loadArtwork(
                imageView,
                viewBinder.albumArtist,
                ArtworkImageLoader.Options.Placeholder(R.drawable.ic_placeholder_artist)
            )

            viewBinder as GridAlbumArtistBinder

            if (viewBinder.coloredBackground) {
                viewBinder.imageLoader.loadColorSet(viewBinder.albumArtist) { newColorSet ->
                    newColorSet?.let {
                        (itemView as CardView).setCardBackgroundColor(newColorSet.primaryColor)
                        title.setTextColor(newColorSet.primaryTextColor)
                        subtitle.setTextColor(newColorSet.primaryTextColor)
                    }
                }
            }

            viewBinder.fixedWidthDp?.let { width ->
                itemView.layoutParams.width = width.dp
            }

            imageView.transitionName = "album_artist_${viewBinder.albumArtist.name}"
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
        }
    }
}