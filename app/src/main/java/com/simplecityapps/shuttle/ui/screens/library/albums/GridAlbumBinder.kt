package com.simplecityapps.shuttle.ui.screens.library.albums

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionViewBinder
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.common.utils.dp
import com.simplecityapps.shuttle.ui.common.view.BadgeView

class GridAlbumBinder(
    album: Album,
    imageLoader: ArtworkImageLoader,
    listener: Listener,
    val showPlayCountBadge: Boolean = false,
    val coloredBackground: Boolean = false,
    val fixedWidthDp: Int? = null
) : AlbumBinder(album, imageLoader, listener),
    SectionViewBinder {

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.grid_item_album, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.AlbumGrid
    }

    override fun spanSize(spanCount: Int): Int {
        return 1
    }


    class ViewHolder(itemView: View) : AlbumBinder.ViewHolder(itemView) {

        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        override val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val badgeView: BadgeView = itemView.findViewById(R.id.badgeImageView)
        private val checkImageView: ImageView = itemView.findViewById(R.id.checkImageView)

        private var animator: ValueAnimator? = null

        init {
            itemView.setOnClickListener {
                viewBinder?.listener?.onAlbumClicked(viewBinder!!.album, this)
            }
            itemView.setOnLongClickListener {
                viewBinder?.listener?.onAlbumLongClicked(viewBinder!!.album, this)
                true
            }
            viewBinder?.listener?.onViewHolderCreated(this)
        }

        override fun bind(viewBinder: AlbumBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.album.name
            subtitle.text = viewBinder.album.albumArtist ?: viewBinder.album.artists.joinToString(", ")

            viewBinder as GridAlbumBinder

            val options = mutableListOf(
                ArtworkImageLoader.Options.Placeholder(R.drawable.ic_placeholder_album),
                ArtworkImageLoader.Options.CacheDecodedResource
            )
            if (viewBinder.coloredBackground) {
                options.add(ArtworkImageLoader.Options.LoadColorSet)
            }

            viewBinder.imageLoader.loadArtwork(
                imageView,
                viewBinder.album,
                options
            ) { colorSet ->
                if (viewBinder.coloredBackground) {
                    (itemView as CardView).setCardBackgroundColor(colorSet.primaryColor)
                    title.setTextColor(colorSet.primaryTextColor)
                    subtitle.setTextColor(colorSet.primaryTextColor)
                    badgeView.setCircleBackgroundColor(colorSet.primaryColor)
                    badgeView.setTextColor(colorSet.primaryTextColor)
                }
            }

            if (viewBinder.showPlayCountBadge) {
                badgeView.badgeCount = viewBinder.album.playCount
                badgeView.isVisible = true
            }

            viewBinder.fixedWidthDp?.let { width ->
                itemView.layoutParams.width = width.dp
            }

            imageView.transitionName = "album_${viewBinder.album.name}"

            checkImageView.isVisible = viewBinder.selected
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
            animator?.cancel()
        }
    }
}