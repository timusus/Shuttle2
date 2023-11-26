package com.simplecityapps.shuttle.ui.screens.library.songs

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.common.phrase.joinSafely
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionViewBinder
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.squareup.phrase.ListPhrase

class GridSongBinder(
    val song: com.simplecityapps.shuttle.model.Song,
    val imageLoader: ArtworkImageLoader,
    val listener: Listener
) : ViewBinder,
    SectionViewBinder {
    interface Listener {
        fun onSongClicked(song: com.simplecityapps.shuttle.model.Song)
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.grid_item_song, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.Song
    }

    override fun getSectionName(): String? {
        return song.name?.firstOrNull()?.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GridSongBinder) return false

        if (song != other.song) return false

        return true
    }

    override fun areContentsTheSame(other: Any): Boolean {
        return song.playCount == (other as? GridSongBinder)?.song?.playCount
    }

    override fun hashCode(): Int {
        return song.hashCode()
    }

    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<GridSongBinder>(itemView) {
        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        private val imageView: ImageView = itemView.findViewById(R.id.imageView)

        private var animator: ValueAnimator? = null

        init {
            itemView.setOnClickListener {
                viewBinder?.listener?.onSongClicked(viewBinder!!.song)
            }
        }

        override fun bind(
            viewBinder: GridSongBinder,
            isPartial: Boolean
        ) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.song.name
            subtitle.text =
                ListPhrase
                    .from(" • ")
                    .joinSafely(
                        listOf(
                            viewBinder.song.friendlyArtistName ?: viewBinder.song.albumArtist,
                            viewBinder.song.album
                        )
                    )

            viewBinder.imageLoader.loadArtwork(
                imageView,
                viewBinder.song,
                listOf(
                    ArtworkImageLoader.Options.Crossfade(200)
                )
            ) { colorSet ->
                (itemView as CardView).setCardBackgroundColor(colorSet.primaryColor)
                title.setTextColor(colorSet.primaryTextColor)
                subtitle.setTextColor(colorSet.primaryTextColor)
            }
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
            animator?.cancel()
        }
    }
}
