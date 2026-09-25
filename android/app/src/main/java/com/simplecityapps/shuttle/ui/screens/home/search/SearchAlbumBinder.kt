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
import com.simplecityapps.mediaprovider.search.SearchQuery
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.getAttrColor
import com.simplecityapps.shuttle.ui.common.joinToSpannedString
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionViewBinder
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.common.utils.dp
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumBinder
import com.squareup.phrase.Phrase

class SearchAlbumBinder(
    album: com.simplecityapps.shuttle.model.Album,
    imageLoader: ArtworkImageLoader,
    listener: Listener,
    private val query: SearchQuery
) : AlbumBinder(album, imageLoader, listener),
    SectionViewBinder {
    override fun createViewHolder(parent: ViewGroup): ViewHolder = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_album, parent, false))

    override fun viewType(): Int = ViewTypes.AlbumList

    override fun areContentsTheSame(other: Any): Boolean {
        if (other !is SearchAlbumBinder) return false

        return super<AlbumBinder>.areContentsTheSame(other) && other.query == query
    }

    class ViewHolder(itemView: View) : AlbumBinder.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        override val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val overflowButton: ImageButton = itemView.findViewById(R.id.overflowButton)
        private val checkImageView: ImageView = itemView.findViewById(R.id.checkImageView)

        private val accentColor = itemView.context.getAttrColor(androidx.appcompat.R.attr.colorAccent)

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

        override fun bind(
            viewBinder: AlbumBinder,
            isPartial: Boolean
        ) {
            super.bind(viewBinder, isPartial)

            viewBinder.imageLoader.loadArtwork(
                imageView = imageView,
                data = viewBinder.album,
                options =
                    listOf(
                        ArtworkImageLoader.Options.RoundedCorners(8.dp),
                        ArtworkImageLoader.Options.Crossfade(200),
                        ArtworkImageLoader.Options.Placeholder(ResourcesCompat.getDrawable(itemView.resources, com.simplecityapps.core.R.drawable.ic_placeholder_album_rounded, itemView.context.theme)!!),
                        ArtworkImageLoader.Options.CacheDecodedResource
                    )
            )

            imageView.transitionName = "album_${viewBinder.album.name}"

            checkImageView.isVisible = viewBinder.selected

            val query = (viewBinder as SearchAlbumBinder).query
            val unknown = itemView.resources.getString(com.simplecityapps.core.R.string.unknown)
            val songQuantity =
                Phrase.fromPlural(itemView.context, R.plurals.songsPlural, viewBinder.album.songCount)
                    .put("count", viewBinder.album.songCount)
                    .format()
            title.text = query.highlight(viewBinder.album.name ?: unknown, accentColor)
            subtitle.text =
                listOf(
                    query.highlight(viewBinder.album.albumArtist ?: viewBinder.album.friendlyArtistName ?: unknown, accentColor),
                    songQuantity
                ).joinToSpannedString(" • ")
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
        }
    }
}
