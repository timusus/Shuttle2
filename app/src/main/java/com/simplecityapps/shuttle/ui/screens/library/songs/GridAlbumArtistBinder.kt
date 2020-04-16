package com.simplecityapps.shuttle.ui.screens.library.songs

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionViewBinder
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes

class GridAlbumArtistBinder(
    val albumArtist: AlbumArtist,
    val imageLoader: ArtworkImageLoader,
    val listener: Listener? = null
) : ViewBinder,
    SectionViewBinder {

    interface Listener {
        fun onAlbumArtistClicked(albumArtist: AlbumArtist, viewHolder: ViewHolder)
        fun onAlbumArtistLongPressed(view: View, albumArtist: AlbumArtist) {}
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.grid_item_album_artist, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.Song
    }

    override fun getSectionName(): String? {
        return albumArtist.name.firstOrNull().toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GridAlbumArtistBinder) return false

        if (albumArtist != other.albumArtist) return false

        return true
    }

    override fun areContentsTheSame(other: Any): Boolean {
        return albumArtist.playCount == (other as? GridAlbumArtistBinder)?.albumArtist?.playCount
    }

    override fun hashCode(): Int {
        return albumArtist.hashCode()
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<GridAlbumArtistBinder>(itemView) {

        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        val imageView: ImageView = itemView.findViewById(R.id.imageView)

        private var animator: ValueAnimator? = null

        init {
            itemView.setOnClickListener {
                viewBinder?.listener?.onAlbumArtistClicked(viewBinder!!.albumArtist, this)
            }
            itemView.setOnLongClickListener {
                viewBinder?.listener?.onAlbumArtistLongPressed(itemView, viewBinder!!.albumArtist)
                true
            }
            subtitle.visibility = View.GONE
        }

        override fun bind(viewBinder: GridAlbumArtistBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.albumArtist.name

            viewBinder.imageLoader.loadArtwork(
                imageView,
                viewBinder.albumArtist,
                ArtworkImageLoader.Options.Crossfade(200)
            )

            viewBinder.imageLoader.loadColorSet(viewBinder.albumArtist) { newColorSet ->
                newColorSet?.let {
                    (itemView as CardView).setCardBackgroundColor(newColorSet.primaryColor)
                    title.setTextColor(newColorSet.primaryTextColor)
                    subtitle.setTextColor(newColorSet.primaryTextColor)
                }
            }

            imageView.transitionName = "album_artist_${viewBinder.albumArtist.name}"
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
            animator?.cancel()
        }
    }
}