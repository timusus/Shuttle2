package com.simplecityapps.shuttle.ui.screens.library.songs

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
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.model.friendlyArtistName
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.getAttrColor
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionViewBinder
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.common.view.BadgeView
import com.simplecityapps.shuttle.ui.screens.home.search.SongJaroSimilarity

open class SongBinder(
    val song: Song,
    val imageLoader: ArtworkImageLoader,
    val listener: Listener,
    val showPlayCountBadge: Boolean = false,
    val jaroSimilarity: SongJaroSimilarity? = null
) : ViewBinder,
    SectionViewBinder {

    var selected: Boolean = false

    interface Listener {
        fun onSongClicked(song: Song)
        fun onSongLongClicked(song: Song)
        fun onOverflowClicked(view: View, song: Song) {}
        fun onViewHolderCreated(holder: ViewHolder) {}
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_song, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.Song
    }

    override fun getSectionName(): String? {
        return song.name?.firstOrNull()?.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SongBinder) return false

        if (song != other.song) return false

        return true
    }

    override fun hashCode(): Int {
        return song.hashCode()
    }

    override fun areContentsTheSame(other: Any): Boolean {
        return other is SongBinder
                && song.name == other.song.name
                && song.albumArtist == other.song.albumArtist
                && song.artists == other.song.artists
                && song.album == other.song.album
                && song.year == other.song.year
                && song.track == other.song.track
                && song.disc == other.song.disc
                && song.playCount == other.song.playCount
                && selected == other.selected
                && showPlayCountBadge == other.showPlayCountBadge
                && jaroSimilarity == other.jaroSimilarity
    }

    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<SongBinder>(itemView) {

        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val overflowButton: ImageButton = itemView.findViewById(R.id.overflowButton)
        private val badgeView: BadgeView = itemView.findViewById(R.id.badgeImageView)
        private val checkImageView: ImageView = itemView.findViewById(R.id.checkImageView)

        private val textColor = itemView.context.getAttrColor(android.R.attr.textColorPrimary)
        private val accentColor = itemView.context.getAttrColor(R.attr.colorAccent)

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

        override fun bind(viewBinder: SongBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.song.name
            subtitle.text = "${viewBinder.song.friendlyArtistName} • ${viewBinder.song.album}"

            val options = mutableListOf(
                ArtworkImageLoader.Options.RoundedCorners(16),
                ArtworkImageLoader.Options.Crossfade(200),
                ArtworkImageLoader.Options.Placeholder(R.drawable.ic_placeholder_song_rounded)
            )

            if (viewBinder.showPlayCountBadge) {
                badgeView.badgeCount = viewBinder.song.playCount
                badgeView.isVisible = true
                options.add(ArtworkImageLoader.Options.LoadColorSet)
            }

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

        private fun highlightMatchedStrings(viewBinder: SongBinder) {
            viewBinder.jaroSimilarity?.let {
                val nameStringBuilder = SpannableStringBuilder(viewBinder.song.name ?: "")
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

                val albumArtistNameStringBuilder = SpannableStringBuilder(viewBinder.song.albumArtist ?: "")
                if (it.albumArtistNameJaroSimilarity.score > 0.8) {
                    it.albumArtistNameJaroSimilarity.bMatchedIndices.forEach { (index, score) ->
                        try {
                            albumArtistNameStringBuilder.setSpan(
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
                val albumNameStringBuilder = SpannableStringBuilder(viewBinder.song.album ?: "")
                if (it.albumNameJaroSimilarity.score > 0.8) {
                    it.albumNameJaroSimilarity.bMatchedIndices.forEach { (index, score) ->
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
                albumArtistNameStringBuilder.append(" • ")
                albumArtistNameStringBuilder.append(albumNameStringBuilder)
                subtitle.text = albumArtistNameStringBuilder
            }
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
        }
    }
}