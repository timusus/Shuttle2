package com.simplecityapps.shuttle.ui.screens.library.songs

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
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.phrase.joinSafely
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionViewBinder
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.common.utils.dp
import com.simplecityapps.shuttle.ui.common.view.BadgeView
import com.squareup.phrase.ListPhrase

open class SongBinder(
    val song: com.simplecityapps.shuttle.model.Song,
    val imageLoader: ArtworkImageLoader,
    val listener: Listener,
    val showPlayCountBadge: Boolean = false
) : ViewBinder,
    SectionViewBinder {

    var selected: Boolean = false

    interface Listener {
        fun onSongClicked(song: com.simplecityapps.shuttle.model.Song)
        fun onSongLongClicked(song: com.simplecityapps.shuttle.model.Song) {}
        fun onOverflowClicked(view: View, song: com.simplecityapps.shuttle.model.Song) {}
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

        if (song.id != other.song.id) return false

        return true
    }

    override fun hashCode(): Int {
        return song.id.hashCode()
    }

    override fun areContentsTheSame(other: Any): Boolean {
        return other is SongBinder
                && song.name == other.song.name
                && song.albumArtist == other.song.albumArtist
                && song.artists == other.song.artists
                && song.album == other.song.album
                && song.date == other.song.date
                && song.track == other.song.track
                && song.disc == other.song.disc
                && song.playCount == other.song.playCount
                && selected == other.selected
                && showPlayCountBadge == other.showPlayCountBadge
    }

    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<SongBinder>(itemView) {

        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val overflowButton: ImageButton = itemView.findViewById(R.id.overflowButton)
        private val badgeView: BadgeView = itemView.findViewById(R.id.badgeImageView)
        private val checkImageView: ImageView = itemView.findViewById(R.id.checkImageView)

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
            subtitle.text = ListPhrase
                .from(" • ")
                .joinSafely(
                    listOf(
                        viewBinder.song.friendlyArtistName ?: viewBinder.song.albumArtist,
                        viewBinder.song.album,
                    )
                ) ?: itemView.resources.getString(R.string.unknown)

            val options = mutableListOf(
                ArtworkImageLoader.Options.RoundedCorners(8.dp),
                ArtworkImageLoader.Options.Crossfade(200),
                ArtworkImageLoader.Options.Placeholder(ResourcesCompat.getDrawable(itemView.resources, R.drawable.ic_placeholder_song_rounded, itemView.context.theme)!!)
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
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
        }
    }
}