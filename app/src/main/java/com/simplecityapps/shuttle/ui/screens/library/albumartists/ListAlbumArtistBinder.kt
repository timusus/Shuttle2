package com.simplecityapps.shuttle.ui.screens.library.albumartists

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.common.utils.dp
import com.squareup.phrase.ListPhrase
import com.squareup.phrase.Phrase

class ListAlbumArtistBinder(
    albumArtist: AlbumArtist,
    imageLoader: ArtworkImageLoader,
    listener: Listener
) : AlbumArtistBinder(albumArtist, imageLoader, listener) {

    override fun createViewHolder(parent: ViewGroup): ViewBinder.ViewHolder<out ViewBinder> {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_album_artist, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.AlbumArtistList
    }

    class ViewHolder(itemView: View) : AlbumArtistBinder.ViewHolder(itemView) {
        private val title = itemView.findViewById<TextView>(R.id.title)
        private val subtitle = itemView.findViewById<TextView>(R.id.subtitle)
        override val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val overflowButton: ImageButton = itemView.findViewById(R.id.overflowButton)
        private val checkImageView: ImageView = itemView.findViewById(R.id.checkImageView)

        init {
            itemView.setOnClickListener { viewBinder?.listener?.onAlbumArtistClicked(viewBinder!!.albumArtist, this) }
            itemView.setOnLongClickListener {
                viewBinder?.listener?.onAlbumArtistLongClicked(itemView, viewBinder!!.albumArtist)
                true
            }
            overflowButton.setOnClickListener {
                viewBinder?.listener?.onOverflowClicked(it, viewBinder!!.albumArtist)
            }
            viewBinder?.listener?.onViewHolderCreated(this)
        }

        override fun bind(viewBinder: AlbumArtistBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.albumArtist.name ?: viewBinder.albumArtist.friendlyArtistName

            val albumQuantity = Phrase
                .fromPlural(itemView.resources, R.plurals.albumsPlural, viewBinder.albumArtist.albumCount)
                .put("count", viewBinder.albumArtist.albumCount)
                .format()
            val songQuantity = Phrase
                .fromPlural(itemView.resources, R.plurals.songsPlural, viewBinder.albumArtist.songCount)
                .put("count", viewBinder.albumArtist.songCount)
                .format()
            subtitle.text = ListPhrase
                .from(" • ")
                .join(albumQuantity, songQuantity)

            viewBinder.imageLoader.loadArtwork(
                imageView = imageView,
                data = viewBinder.albumArtist,
                options = listOf(
                    ArtworkImageLoader.Options.RoundedCorners(8.dp),
                    ArtworkImageLoader.Options.Crossfade(200),
                    ArtworkImageLoader.Options.Placeholder(R.drawable.ic_placeholder_artist_rounded),
                    ArtworkImageLoader.Options.CacheDecodedResource
                )
            )
            imageView.transitionName = "album_artist_${viewBinder.albumArtist.name ?: viewBinder.albumArtist.friendlyArtistName}"

            checkImageView.isVisible = viewBinder.selected
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
        }
    }
}