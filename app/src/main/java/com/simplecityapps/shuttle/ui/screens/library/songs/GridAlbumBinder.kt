package com.simplecityapps.shuttle.ui.screens.library.songs

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionViewBinder
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.common.view.BadgeView

class GridAlbumBinder(
    val album: Album,
    val imageLoader: ArtworkImageLoader,
    val showPlayCountBadge: Boolean = false,
    val listener: Listener? = null
) : ViewBinder,
    SectionViewBinder {

    interface Listener {
        fun onAlbumClicked(album: Album, viewHolder: ViewHolder)
        fun onAlbumLongPressed(view: View, album: Album) {}
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.grid_item_album, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.Song
    }

    override fun getSectionName(): String? {
        return album.name.firstOrNull().toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GridAlbumBinder) return false

        if (album != other.album) return false

        return true
    }

    override fun areContentsTheSame(other: Any): Boolean {
        return album.playCount == (other as? GridAlbumBinder)?.album?.playCount
    }

    override fun hashCode(): Int {
        return album.hashCode()
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<GridAlbumBinder>(itemView) {

        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val badgeView: BadgeView = itemView.findViewById(R.id.badgeImageView)

        private var animator: ValueAnimator? = null

        init {
            itemView.setOnClickListener {
                viewBinder?.listener?.onAlbumClicked(viewBinder!!.album, this)
            }
            itemView.setOnLongClickListener {
                viewBinder?.listener?.onAlbumLongPressed(itemView, viewBinder!!.album)
                true
            }
        }

        override fun bind(viewBinder: GridAlbumBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.album.name
            subtitle.text = "${viewBinder.album.albumArtist}"

            viewBinder.imageLoader.loadArtwork(
                imageView,
                viewBinder.album,
                ArtworkImageLoader.Options.Crossfade(200),
                ArtworkImageLoader.Options.Placeholder(R.drawable.ic_placeholder_album)
            )

            viewBinder.imageLoader.loadColorSet(viewBinder.album) { newColorSet ->
                newColorSet?.let {
                    (itemView as CardView).setCardBackgroundColor(newColorSet.primaryColor)
                    title.setTextColor(newColorSet.primaryTextColor)
                    subtitle.setTextColor(newColorSet.primaryTextColor)
                    badgeView.setCircleBackgroundColor(newColorSet.primaryColor)
                    badgeView.setTextColor(newColorSet.primaryTextColor)
                }
            }

            if (viewBinder.showPlayCountBadge) {
                badgeView.badgeCount = viewBinder.album.playCount
                badgeView.isVisible = true
            }

            imageView.transitionName = "album_${viewBinder.album.name}"
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
            animator?.cancel()
        }
    }
}