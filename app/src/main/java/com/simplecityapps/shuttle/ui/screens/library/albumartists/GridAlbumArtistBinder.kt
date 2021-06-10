package com.simplecityapps.shuttle.ui.screens.library.albumartists

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
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
        private val checkImageView: ImageView = itemView.findViewById(R.id.checkImageView)

        init {
            itemView.setOnClickListener {
                viewBinder?.listener?.onAlbumArtistClicked(viewBinder!!.albumArtist, this)
            }
            itemView.setOnLongClickListener {
                viewBinder?.listener?.onAlbumArtistLongClicked(itemView, viewBinder!!.albumArtist)
                true
            }
            subtitle.visibility = View.GONE

            viewBinder?.listener?.onViewHolderCreated(this)
        }

        override fun bind(viewBinder: AlbumArtistBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.albumArtist.friendlyNameOrArtistName ?: itemView.resources.getString(R.string.unknown)

            viewBinder as GridAlbumArtistBinder

            viewBinder.fixedWidthDp?.let { width ->
                itemView.layoutParams.width = width.dp
            }

            val options = mutableListOf(
                ArtworkImageLoader.Options.Placeholder(R.drawable.ic_placeholder_artist),
                ArtworkImageLoader.Options.CacheDecodedResource
            )
            if (viewBinder.coloredBackground) {
                options.add(ArtworkImageLoader.Options.LoadColorSet)
            }
            viewBinder.imageLoader.loadArtwork(
                imageView,
                viewBinder.albumArtist,
                options
            ) { colorSet ->
                (itemView as CardView).setCardBackgroundColor(colorSet.primaryColor)
                title.setTextColor(colorSet.primaryTextColor)
                subtitle.setTextColor(colorSet.primaryTextColor)
            }

            imageView.transitionName = "album_artist_${viewBinder.albumArtist.friendlyNameOrArtistName}"

            checkImageView.isVisible = viewBinder.selected
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
        }
    }
}