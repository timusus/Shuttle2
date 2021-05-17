package com.simplecityapps.shuttle.ui.screens.settings

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
import com.squareup.phrase.ListPhrase

class ExcludeBinder(
    val song: Song,
    val imageLoader: ArtworkImageLoader,
    val listener: Listener? = null
) : ViewBinder,
    SectionViewBinder {

    interface Listener {
        fun onRemoveClicked(song: Song)
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_exclude, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.ExcludeList
    }

    override fun getSectionName(): String? {
        return song.name?.firstOrNull()?.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExcludeBinder) return false

        if (song != other.song) return false

        return true
    }

    override fun hashCode(): Int {
        return song.hashCode()
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<ExcludeBinder>(itemView) {

        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        private val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val removeButton: ImageButton = itemView.findViewById(R.id.removeButton)

        init {
            removeButton.setOnClickListener {
                viewBinder?.listener?.onRemoveClicked(viewBinder!!.song)
            }
        }

        override fun bind(viewBinder: ExcludeBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.song.name
            subtitle.text = ListPhrase.from(" • ").join(listOf(viewBinder.song.albumArtist, viewBinder.song.album))
            viewBinder.imageLoader.loadArtwork(
                imageView,
                viewBinder.song,
                listOf(
                    ArtworkImageLoader.Options.RoundedCorners(16),
                    ArtworkImageLoader.Options.Crossfade(200)
                )
            )
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
        }
    }
}