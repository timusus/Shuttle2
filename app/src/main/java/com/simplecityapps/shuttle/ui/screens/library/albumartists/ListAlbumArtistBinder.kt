package com.simplecityapps.shuttle.ui.screens.library.albumartists

import android.animation.ArgbEvaluator
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
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
import com.simplecityapps.mediaprovider.model.friendlyNameOrArtistName
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.getAttrColor
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.screens.home.search.ArtistJaroSimilarity

class ListAlbumArtistBinder(
    albumArtist: AlbumArtist,
    imageLoader: ArtworkImageLoader,
    listener: Listener,
    jaroSimilarity: ArtistJaroSimilarity? = null
) : AlbumArtistBinder(albumArtist, imageLoader, listener, jaroSimilarity) {

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

        private val textColor = itemView.context.getAttrColor(android.R.attr.textColorPrimary)
        private val accentColor = itemView.context.getAttrColor(R.attr.colorAccent)

        init {
            itemView.setOnClickListener { viewBinder?.listener?.onAlbumArtistClicked(viewBinder!!.albumArtist, this) }
            itemView.setOnLongClickListener {
                viewBinder?.listener?.onAlbumArtistLongClicked(itemView, viewBinder!!.albumArtist)
                true
            }
            overflowButton.setOnClickListener {
                viewBinder?.listener?.onOverflowClicked(it, viewBinder!!.albumArtist)
            }
        }

        override fun bind(viewBinder: AlbumArtistBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.albumArtist.friendlyNameOrArtistName
            subtitle.text = "${
                subtitle.resources.getQuantityString(
                    R.plurals.albumsPlural,
                    viewBinder.albumArtist.albumCount,
                    viewBinder.albumArtist.albumCount
                )
            } • ${subtitle.resources.getQuantityString(R.plurals.songsPlural, viewBinder.albumArtist.songCount, viewBinder.albumArtist.songCount)}"
            viewBinder.imageLoader.loadArtwork(
                imageView, viewBinder.albumArtist,
                listOf(
                    ArtworkImageLoader.Options.RoundedCorners(16),
                    ArtworkImageLoader.Options.Crossfade(200),
                    ArtworkImageLoader.Options.Placeholder(R.drawable.ic_placeholder_artist_rounded)
                )
            )
            imageView.transitionName = "album_artist_${viewBinder.albumArtist.friendlyNameOrArtistName}"

            checkImageView.isVisible = viewBinder.selected

            highlightMatchedStrings(viewBinder)
        }

        private fun highlightMatchedStrings(viewBinder: AlbumArtistBinder) {
            viewBinder.jaroSimilarity?.let {
                val nameStringBuilder = SpannableStringBuilder(viewBinder.albumArtist.friendlyNameOrArtistName)
                if (it.albumArtistNameJaroSimilarity.score > 0.8) {
                    it.albumArtistNameJaroSimilarity.bMatchedIndices.forEach { (index, score) ->
                        try {
                            nameStringBuilder.setSpan(
                                ForegroundColorSpan(ArgbEvaluator().evaluate(score.toFloat() - 0.25f, textColor, accentColor) as Int),
                                index,
                                index + 1,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        } catch (e: IndexOutOfBoundsException) {
                            // This is possible because the jaro similarity function does string normalisation, so we're not necessarily using the exact same string
                        }
                    }
                }
                title.text = nameStringBuilder
            }
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
        }
    }
}


