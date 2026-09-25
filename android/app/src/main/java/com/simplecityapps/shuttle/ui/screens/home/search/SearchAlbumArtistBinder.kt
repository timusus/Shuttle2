package com.simplecityapps.shuttle.ui.screens.home.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.search.SearchQuery
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.getAttrColor
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.common.utils.dp
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistBinder
import com.squareup.phrase.ListPhrase
import com.squareup.phrase.Phrase

class SearchAlbumArtistBinder(
    albumArtist: com.simplecityapps.shuttle.model.AlbumArtist,
    imageLoader: ArtworkImageLoader,
    listener: Listener,
    private val query: SearchQuery
) : AlbumArtistBinder(albumArtist, imageLoader, listener) {
    override fun createViewHolder(parent: ViewGroup): ViewBinder.ViewHolder<out ViewBinder> = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_album_artist, parent, false))

    override fun viewType(): Int = ViewTypes.AlbumArtistList

    override fun areContentsTheSame(other: Any): Boolean {
        if (other !is SearchAlbumArtistBinder) return false

        return super.areContentsTheSame(other) && other.query == query
    }

    class ViewHolder(itemView: View) : AlbumArtistBinder.ViewHolder(itemView) {
        private val title = itemView.findViewById<TextView>(R.id.title)
        private val subtitle = itemView.findViewById<TextView>(R.id.subtitle)
        override val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val overflowButton: ImageButton = itemView.findViewById(R.id.overflowButton)
        private val checkImageView: ImageView = itemView.findViewById(R.id.checkImageView)

        private val accentColor = itemView.context.getAttrColor(androidx.appcompat.R.attr.colorAccent)

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

        override fun bind(
            viewBinder: AlbumArtistBinder,
            isPartial: Boolean
        ) {
            super.bind(viewBinder, isPartial)

            val albumQuantity =
                Phrase
                    .fromPlural(itemView.resources, R.plurals.albumsPlural, viewBinder.albumArtist.albumCount)
                    .put("count", viewBinder.albumArtist.albumCount)
                    .format()
            val songQuantity =
                Phrase
                    .fromPlural(itemView.resources, R.plurals.songsPlural, viewBinder.albumArtist.songCount)
                    .put("count", viewBinder.albumArtist.songCount)
                    .format()
            subtitle.text =
                ListPhrase
                    .from(" • ")
                    .join(albumQuantity, songQuantity)

            viewBinder.imageLoader.loadArtwork(
                imageView = imageView,
                data = viewBinder.albumArtist,
                options =
                    listOf(
                        ArtworkImageLoader.Options.RoundedCorners(8.dp),
                        ArtworkImageLoader.Options.Crossfade(200),
                        ArtworkImageLoader.Options.Placeholder(ResourcesCompat.getDrawable(itemView.resources, com.simplecityapps.core.R.drawable.ic_placeholder_artist_rounded, itemView.context.theme)!!),
                        ArtworkImageLoader.Options.CacheDecodedResource
                    )
            )
            imageView.transitionName = "album_artist_${viewBinder.albumArtist.name ?: viewBinder.albumArtist.friendlyArtistName}"

            checkImageView.isVisible = viewBinder.selected

            val name = viewBinder.albumArtist.name ?: viewBinder.albumArtist.friendlyArtistName
            title.text = name?.let { (viewBinder as SearchAlbumArtistBinder).query.highlight(it, accentColor) }
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
        }
    }
}
