package com.simplecityapps.shuttle.ui.screens.playback

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes

class ArtworkBinder(
    val queueItem: QueueItem,
    val imageLoader: ArtworkImageLoader
) : ViewBinder {

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_artwork, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.Artwork
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ArtworkBinder

        if (queueItem != other.queueItem) return false

        return true
    }

    override fun hashCode(): Int {
        return queueItem.hashCode()
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<ArtworkBinder>(itemView) {

        val imageView: ImageView = itemView.findViewById(R.id.imageView)

        override fun bind(viewBinder: ArtworkBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            viewBinder.imageLoader.loadArtwork(
                imageView, viewBinder.queueItem.song,
                listOf(
                    ArtworkImageLoader.Options.RoundedCorners(32),
                    ArtworkImageLoader.Options.Crossfade(200),
                    ArtworkImageLoader.Options.Placeholder(R.drawable.ic_placeholder_song)
                )
            )
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
        }
    }
}