package com.simplecityapps.shuttle.ui.screens.library.songs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionViewBinder
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes

class SongBinder(
    val song: Song,
    val imageLoader: ArtworkImageLoader,
    val listener: Listener? = null
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


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<SongBinder>(itemView) {

        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        private val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val overflowButton: ImageButton = itemView.findViewById(R.id.overflowButton)

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
            subtitle.text = "${viewBinder.song.albumArtistName} • ${viewBinder.song.albumName}"
            viewBinder.imageLoader.loadArtwork(
                imageView,
                viewBinder.song,
                ArtworkImageLoader.Options.RoundedCorners(16),
                ArtworkImageLoader.Options.Crossfade(200),
                ArtworkImageLoader.Options.Placeholder(R.drawable.ic_placeholder_song_rounded)
            )
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
        }
    }
}