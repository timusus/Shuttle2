package com.simplecityapps.shuttle.ui.screens.home.search

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
import com.simplecityapps.mediaprovider.StringComparison
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.getAttrColor
import com.simplecityapps.shuttle.ui.common.joinToSpannedString
import com.simplecityapps.shuttle.ui.common.phrase.joinSafely
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionViewBinder
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.common.utils.dp
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumBinder
import com.squareup.phrase.ListPhrase
import com.squareup.phrase.Phrase
import timber.log.Timber

class SearchAlbumBinder(
    album: Album,
    imageLoader: ArtworkImageLoader,
    listener: Listener,
    private val jaroSimilarity: AlbumJaroSimilarity
) : AlbumBinder(album, imageLoader, listener),
    SectionViewBinder {

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_album, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.AlbumList
    }

    override fun areContentsTheSame(other: Any): Boolean {
        if (other !is SearchAlbumBinder) return false

        return super<AlbumBinder>.areContentsTheSame(other) && other.jaroSimilarity == jaroSimilarity
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
                    items = listOf(
                        viewBinder.album.albumArtist ?: viewBinder.album.friendlyArtistName,
                        songQuantity
                    ),
                    defaultValue = itemView.resources.getString(R.string.unknown)
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

            highlightMatchedStrings(viewBinder as SearchAlbumBinder, songQuantity)
        }

        private fun highlightMatchedStrings(viewBinder: SearchAlbumBinder, songQuantity: CharSequence) {
            viewBinder.album.name?.let {
                if (viewBinder.jaroSimilarity.nameJaroSimilarity.score >= StringComparison.threshold) {
                    val nameStringBuilder = SpannableStringBuilder(viewBinder.album.name)
                    viewBinder.jaroSimilarity.nameJaroSimilarity.bMatchedIndices.forEach { (index, score) ->
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
                    title.text = nameStringBuilder
                }
            }

            viewBinder.album.albumArtist ?: viewBinder.album.friendlyArtistName?.let {
                if (viewBinder.jaroSimilarity.albumArtistNameJaroSimilarity.score >= StringComparison.threshold) {
                    val artistNameStringBuilder = SpannableStringBuilder(viewBinder.album.albumArtist ?: viewBinder.album.friendlyArtistName)
                    viewBinder.jaroSimilarity.albumArtistNameJaroSimilarity.bMatchedIndices.forEach { (index, score) ->
                        try {
                            artistNameStringBuilder.setSpan(
                                ForegroundColorSpan(ArgbEvaluator().evaluate(score.toFloat() - 0.25f, textColor, accentColor) as Int),
                                index,
                                index + 1,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        } catch (e: IndexOutOfBoundsException) {
                            Timber.e(e, "Error")
                            // This is possible because the jaro similarity function does string normalisation, so we're not necessarily using the exact same string
                        }
                    }
                    subtitle.text = listOf(
                        artistNameStringBuilder,
                        songQuantity
                    ).joinToSpannedString(" • ")
                }
            }
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
        }
    }
}