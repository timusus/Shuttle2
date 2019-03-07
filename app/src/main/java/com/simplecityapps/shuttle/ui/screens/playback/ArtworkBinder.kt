package com.simplecityapps.shuttle.ui.screens.playback

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R

class ArtworkBinder(val song: Song, val imageLoader: ArtworkImageLoader) : ViewBinder {

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_artwork, parent, false))
    }

    override fun viewType(): ViewBinder.ViewType {
        return ViewBinder.ViewType.Artwork
    }

    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<ArtworkBinder>(itemView) {

        val imageView: ImageView = itemView.findViewById(R.id.imageView)

        override fun bind(viewBinder: ArtworkBinder) {
            super.bind(viewBinder)

            viewBinder.imageLoader.loadArtwork(imageView, viewBinder.song, ArtworkImageLoader.Options.RoundedCorners(32))
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
        }
    }
}