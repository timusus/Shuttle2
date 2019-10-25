package com.simplecityapps.shuttle.ui.screens.library.songs

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecity.amp_library.glide.palette.ColorSet
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionViewBinder
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes

class GridSongBinder(
    val song: Song,
    val imageLoader: ArtworkImageLoader,
    val listener: Listener
) : ViewBinder,
    SectionViewBinder {

    interface Listener {
        fun onSongClicked(song: Song)
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.grid_item_song, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.Song
    }

    override fun getSectionName(): String? {
        return song.name.firstOrNull().toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GridSongBinder) return false

        if (song != other.song) return false

        return true
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

        override fun bind(viewBinder: GridSongBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.song.name
            subtitle.text = "${viewBinder.song.albumArtistName} • ${viewBinder.song.albumName}"

            val initialColorSet = ColorSet(
                (itemView as CardView).cardBackgroundColor.defaultColor,
                itemView.cardBackgroundColor.defaultColor,
                itemView.cardBackgroundColor.defaultColor,
                itemView.cardBackgroundColor.defaultColor,
                itemView.cardBackgroundColor.defaultColor,
                itemView.cardBackgroundColor.defaultColor
            )

            viewBinder.imageLoader.loadArtwork(
                imageView,
                viewBinder.song,
                ArtworkImageLoader.Options.Crossfade(200)
            )

            viewBinder.imageLoader.loadColorSet(viewBinder.song) { newColorSet ->
                newColorSet?.let {

//                    animator = ObjectAnimator.ofObject(ColorSetEvaluator(), initialColorSet, newColorSet)
//                        .setDuration(250)
//
//                    animator?.addUpdateListener { value ->
                        itemView.setCardBackgroundColor(newColorSet.primaryColor)
                        title.setTextColor(newColorSet.primaryTextColor)
                        subtitle.setTextColor(newColorSet.primaryTextColor)
//                    }
//
//                    animator?.start()
                }
            }
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
            animator?.cancel()
        }
    }
}