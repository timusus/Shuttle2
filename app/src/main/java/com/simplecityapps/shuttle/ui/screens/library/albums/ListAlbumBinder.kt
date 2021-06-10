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
import com.simplecityapps.shuttle.ui.common.phrase.joinSafely
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionViewBinder
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.common.utils.dp
import com.squareup.phrase.ListPhrase
import com.squareup.phrase.Phrase

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
            viewBinder?.listener?.onViewHolderCreated(this)
        }

        override fun bind(viewBinder: AlbumBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.album.name ?: itemView.resources.getString(R.string.unknown)
            val songQuantity = Phrase.fromPlural(itemView.context, R.plurals.songsPlural, viewBinder.album.songCount)
                .put("count", viewBinder.album.songCount)
                .format()
            subtitle.text = ListPhrase
                .from(" • ")
                .joinSafely(
                    listOf(
                        viewBinder.album.friendlyAlbumArtistOrArtistName,
                        songQuantity
                    )
                )

            viewBinder.imageLoader.loadArtwork(
                imageView = imageView,
                data = viewBinder.album,
                options = listOf(
                    ArtworkImageLoader.Options.RoundedCorners(8.dp),
                    ArtworkImageLoader.Options.Crossfade(200),
                    ArtworkImageLoader.Options.Placeholder(R.drawable.ic_placeholder_album_rounded),
                    ArtworkImageLoader.Options.CacheDecodedResource
                )
            )

            imageView.transitionName = "album_${viewBinder.album.name}"

            checkImageView.isVisible = viewBinder.selected
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
        }
    }
}