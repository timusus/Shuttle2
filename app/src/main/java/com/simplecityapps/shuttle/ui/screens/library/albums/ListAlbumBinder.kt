package com.simplecityapps.shuttle.ui.screens.library.albums

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
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.friendlyAlbumArtistOrArtistName
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.getAttrColor
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionViewBinder
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.screens.home.search.AlbumJaroSimilarity

class ListAlbumBinder(
    album: Album,
    imageLoader: ArtworkImageLoader,
    listener: Listener,
    jaroSimilarity: AlbumJaroSimilarity? = null
) : AlbumBinder(album, imageLoader, listener, jaroSimilarity),
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

        private val textColor = itemView.context.getAttrColor(android.R.attr.textColorPrimary)
        private val accentColor = itemView.context.getAttrColor(R.attr.colorAccent)

        init {
            itemView.setOnClickListener { viewBinder?.listener?.onAlbumClicked(viewBinder!!.album, this) }
            itemView.setOnLongClickListener {
                viewBinder?.listener?.onAlbumLongClicked(viewBinder!!.album, this)
                true
            }
            overflowButton.setOnClickListener {
                viewBinder?.listener?.onOverflowClicked(it, viewBinder!!.album)
            }
        }

        override fun bind(viewBinder: AlbumBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.album.name
            subtitle.text = "${viewBinder.album.albumArtist ?: viewBinder.album.artists.joinToString(", ")} • ${
                subtitle.resources.getQuantityString(
                    R.plurals.songsPlural,
                    viewBinder.album.songCount,
                    viewBinder.album.songCount
                )
            }"

            viewBinder.imageLoader.loadArtwork(
                imageView, viewBinder.album,
                listOf(
                    ArtworkImageLoader.Options.RoundedCorners(16),
                    ArtworkImageLoader.Options.Crossfade(200),
                    ArtworkImageLoader.Options.Placeholder(R.drawable.ic_placeholder_album_rounded)
                )
            )

            imageView.transitionName = "album_${viewBinder.album.name}"

            checkImageView.isVisible = viewBinder.selected

            highlightMatchedStrings(viewBinder)
        }

        private fun highlightMatchedStrings(viewBinder: AlbumBinder) {
            viewBinder.jaroSimilarity?.let {
                val nameStringBuilder = SpannableStringBuilder(viewBinder.album.name ?: "")
                if (it.nameJaroSimilarity.score > 0.8) {
                    it.nameJaroSimilarity.bMatchedIndices.forEach { (index, score) ->
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

                val artistNameStringBuilder = SpannableStringBuilder(viewBinder.album.friendlyAlbumArtistOrArtistName)
                if (it.albumArtistNameJaroSimilarity.score > 0.8) {
                    it.albumArtistNameJaroSimilarity.bMatchedIndices.forEach { (index, score) ->
                        try {
                            artistNameStringBuilder.setSpan(
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
                artistNameStringBuilder.append(" • ${subtitle.resources.getQuantityString(R.plurals.songsPlural, viewBinder.album.songCount, viewBinder.album.songCount)}")
                subtitle.text = artistNameStringBuilder
            }
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
        }
    }
}