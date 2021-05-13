package com.simplecityapps.shuttle.ui.common.dialog.artwork

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.local.ArtworkLocation
import au.com.simplecityapps.shuttle.imageloading.glide.loader.local.WrappedArtworkModel
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.common.utils.dp

sealed class ArtworkType(val name: String) {
    class Remote(name: String, val url: String) : ArtworkType(name)
    class Local(val wrappedArtworkModel: WrappedArtworkModel) : ArtworkType(
        when (wrappedArtworkModel.location) {
            ArtworkLocation.Directory -> "Disk"
            ArtworkLocation.Tags -> "Tags"
        }
    )
}

class ArtworkImageBinder(val imageLoader: ArtworkImageLoader, val artworkType: ArtworkType) : ViewBinder {

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_artwork_editing, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.Artwork
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<ArtworkImageBinder>(itemView) {
        val title: TextView = itemView.findViewById(R.id.title)
        val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        val imageView: ImageView = itemView.findViewById(R.id.imageView)

        override fun bind(viewBinder: ArtworkImageBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.artworkType.name

            when (val artworkType = viewBinder.artworkType) {
                is ArtworkType.Remote -> {
                    viewBinder.imageLoader.loadArtwork(
                        imageView = imageView,
                        data = artworkType.url,
                        options = listOf(
                            ArtworkImageLoader.Options.SkipCache,
                            ArtworkImageLoader.Options.RoundedCorners(8.dp),
                            ArtworkImageLoader.Options.Placeholder(R.drawable.ic_placeholder_artist)
                        )
                    )
                }
                is ArtworkType.Local -> {
                    viewBinder.imageLoader.loadArtwork(
                        imageView = imageView,
                        data = artworkType.wrappedArtworkModel,
                        options = listOf(
                            ArtworkImageLoader.Options.SkipCache,
                            ArtworkImageLoader.Options.RoundedCorners(8.dp),
                            ArtworkImageLoader.Options.Placeholder(R.drawable.ic_placeholder_artist)
                        ),
                        onCompletion = { result ->
                            result.onFailure {

                            }
                        }
                    )
                }
            }
        }
    }
}