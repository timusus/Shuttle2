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
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.StringComparison
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.getAttrColor
import com.simplecityapps.shuttle.ui.common.joinToSpannedString
import com.simplecityapps.shuttle.ui.common.phrase.joinSafely
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionViewBinder
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.common.utils.dp
import com.simplecityapps.shuttle.ui.common.view.BadgeView
import com.squareup.phrase.ListPhrase

open class SearchSongBinder(
    val song: com.simplecityapps.shuttle.model.Song,
    val imageLoader: ArtworkImageLoader,
    val listener: Listener,
    val jaroSimilarity: SongJaroSimilarity
) : ViewBinder,
    SectionViewBinder {
    var selected: Boolean = false

    interface Listener {
        fun onSongClicked(song: com.simplecityapps.shuttle.model.Song)

        fun onSongLongClicked(song: com.simplecityapps.shuttle.model.Song) {}

        fun onOverflowClicked(
            view: View,
            song: com.simplecityapps.shuttle.model.Song
        ) {}

        fun onViewHolderCreated(holder: ViewHolder) {}
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_song, parent, false))

    override fun viewType(): Int = ViewTypes.Song

    override fun getSectionName(): String? = song.name?.firstOrNull()?.toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SearchSongBinder) return false

        if (song.id != other.song.id) return false

        return true
    }

    override fun hashCode(): Int = song.id.hashCode()

    override fun areContentsTheSame(other: Any): Boolean = other is SearchSongBinder &&
        song.name == other.song.name &&
        song.albumArtist == other.song.albumArtist &&
        song.artists == other.song.artists &&
        song.album == other.song.album &&
        song.date == other.song.date &&
        song.track == other.song.track &&
        song.disc == other.song.disc &&
        song.playCount == other.song.playCount &&
        selected == other.selected &&
        jaroSimilarity == other.jaroSimilarity

    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<SearchSongBinder>(itemView) {
        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val overflowButton: ImageButton = itemView.findViewById(R.id.overflowButton)
        private val badgeView: BadgeView = itemView.findViewById(R.id.badgeImageView)
        private val checkImageView: ImageView = itemView.findViewById(R.id.checkImageView)

        private val textColor = itemView.context.getAttrColor(android.R.attr.textColorPrimary)
        private val accentColor = itemView.context.getAttrColor(androidx.appcompat.R.attr.colorAccent)

        init {
            itemView.setOnClickListener {
                viewBinder?.listener?.onSongClicked(viewBinder!!.song)
            }
            overflowButton.setOnClickListener {
                viewBinder?.listener?.onOverflowClicked(it, viewBinder!!.song)
            }
            itemView.setOnLongClickListener {
                viewBinder?.listener?.onSongLongClicked(viewBinder!!.song)
                true
            }
            viewBinder?.listener?.onViewHolderCreated(this)
        }

        override fun bind(
            viewBinder: SearchSongBinder,
            isPartial: Boolean
        ) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.song.name ?: itemView.resources.getString(com.simplecityapps.core.R.string.unknown)
            subtitle.text =
                ListPhrase
                    .from(" • ")
                    .joinSafely(
                        items =
                        listOf(
                            viewBinder.song.friendlyArtistName ?: viewBinder.song.albumArtist,
                            viewBinder.song.album
                        ),
                        defaultValue = itemView.resources.getString(com.simplecityapps.core.R.string.unknown)
                    )

            val options =
                mutableListOf(
                    ArtworkImageLoader.Options.RoundedCorners(8.dp),
                    ArtworkImageLoader.Options.Crossfade(200),
                    ArtworkImageLoader.Options.Placeholder(ResourcesCompat.getDrawable(itemView.resources, com.simplecityapps.core.R.drawable.ic_placeholder_song_rounded, itemView.context.theme)!!)
                )

            viewBinder.imageLoader.loadArtwork(
                imageView,
                viewBinder.song,
                options
            ) { colorSet ->
                badgeView.setCircleBackgroundColor(colorSet.primaryColor)
                badgeView.setTextColor(colorSet.primaryTextColor)
            }

            checkImageView.isVisible = viewBinder.selected

            highlightMatchedStrings(viewBinder)
        }

        private fun highlightMatchedStrings(viewBinder: SearchSongBinder) {
            viewBinder.song.name?.let {
                val nameStringBuilder = SpannableStringBuilder(viewBinder.song.name)
                if (viewBinder.jaroSimilarity.nameJaroSimilarity.score >= StringComparison.threshold) {
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
                }
                title.text = nameStringBuilder
            }

            // We display either the artist, or album-artist - whichever gave us a better jaro score
            var artistOrAlbumArtistStringBuilder: SpannableStringBuilder?
            if (viewBinder.jaroSimilarity.artistNameJaroSimilarity.score >= viewBinder.jaroSimilarity.albumArtistNameJaroSimilarity.score ||
                (
                    viewBinder.jaroSimilarity.artistNameJaroSimilarity.score < StringComparison.threshold &&
                        viewBinder.jaroSimilarity.albumArtistNameJaroSimilarity.score < StringComparison.threshold
                    )
            ) {
                artistOrAlbumArtistStringBuilder = viewBinder.song.friendlyArtistName?.let { SpannableStringBuilder(viewBinder.song.friendlyArtistName) }
                artistOrAlbumArtistStringBuilder?.let {
                    if (viewBinder.jaroSimilarity.artistNameJaroSimilarity.score >= StringComparison.threshold) {
                        viewBinder.jaroSimilarity.artistNameJaroSimilarity.bMatchedIndices.forEach { (index, score) ->
                            try {
                                artistOrAlbumArtistStringBuilder?.setSpan(
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
                }
            } else {
                artistOrAlbumArtistStringBuilder = viewBinder.song.albumArtist?.let { SpannableStringBuilder(viewBinder.song.albumArtist) }
                artistOrAlbumArtistStringBuilder?.let {
                    if (viewBinder.jaroSimilarity.albumArtistNameJaroSimilarity.score >= StringComparison.threshold) {
                        viewBinder.jaroSimilarity.albumArtistNameJaroSimilarity.bMatchedIndices.forEach { (index, score) ->
                            try {
                                artistOrAlbumArtistStringBuilder?.setSpan(
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
                }
            }

            val albumNameStringBuilder = viewBinder.song.album?.let { SpannableStringBuilder(viewBinder.song.album) }
            albumNameStringBuilder?.let {
                if (viewBinder.jaroSimilarity.albumNameJaroSimilarity.score >= StringComparison.threshold) {
                    viewBinder.jaroSimilarity.albumNameJaroSimilarity.bMatchedIndices.forEach { (index, score) ->
                        try {
                            albumNameStringBuilder.setSpan(
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
            }

            subtitle.text =
                listOfNotNull(
                    artistOrAlbumArtistStringBuilder,
                    albumNameStringBuilder
                )
                    .joinToSpannedString(" • ")
                    .ifEmpty { itemView.resources.getString(com.simplecityapps.core.R.string.unknown) }
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
        }
    }
}
