package com.simplecityapps.shuttle.ui.screens.library.songs

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
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionViewBinder
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.common.view.BadgeView

class SongBinder(
    val song: Song,
    val imageLoader: ArtworkImageLoader,
    val listener: Listener,
    val showPlayCountBadge: Boolean = false,
) : ViewBinder,
    SectionViewBinder {

    interface Listener {
        fun onSongClicked(song: Song)
        fun onOverflowClicked(view: View, song: Song) {}
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_song, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.Song
    }

    override fun getSectionName(): String? {
        return song.name.firstOrNull().toString()
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
                && song.artist == other.song.artist
                && song.album == other.song.album
                && song.year == other.song.year
                && song.track == other.song.track
                && song.disc == other.song.disc
    }

    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<SongBinder>(itemView) {

        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        private val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val overflowButton: ImageButton = itemView.findViewById(R.id.overflowButton)
        private val badgeView: BadgeView = itemView.findViewById(R.id.badgeImageView)

        init {
            itemView.setOnClickListener {
                viewBinder?.listener?.onSongClicked(viewBinder!!.song)
            }
            overflowButton.setOnClickListener {
                viewBinder?.listener?.onOverflowClicked(it, viewBinder!!.song)
            }
        }

        override fun bind(viewBinder: SongBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.song.name
            subtitle.text = "${viewBinder.song.albumArtist} • ${viewBinder.song.album}"
            viewBinder.imageLoader.loadArtwork(
                imageView,
                viewBinder.song,
                ArtworkImageLoader.Options.RoundedCorners(16),
                ArtworkImageLoader.Options.Crossfade(200),
                ArtworkImageLoader.Options.Placeholder(R.drawable.ic_placeholder_song_rounded)
            )

            if (viewBinder.showPlayCountBadge) {
                badgeView.badgeCount = viewBinder.song.playCount
                badgeView.isVisible = true

                viewBinder.imageLoader.loadColorSet(viewBinder.song) { newColorSet ->
                    newColorSet?.let {
                        badgeView.setCircleBackgroundColor(newColorSet.primaryColor)
                        badgeView.setTextColor(newColorSet.primaryTextColor)
                    }
                }
            }
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
        }
    }
}