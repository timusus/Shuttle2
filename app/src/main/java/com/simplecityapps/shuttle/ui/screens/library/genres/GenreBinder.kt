package com.simplecityapps.shuttle.ui.screens.library.genres

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Genre
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.squareup.phrase.Phrase

class GenreBinder(val genre: Genre, private val listener: Listener) : ViewBinder {

    interface Listener {
        fun onGenreSelected(genre: Genre, viewHolder: ViewHolder)
        fun onOverflowClicked(view: View, genre: Genre) {}
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_genre, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.Genre
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GenreBinder

        if (genre != other.genre) return false

        return true
    }

    override fun hashCode(): Int {
        return genre.hashCode()
    }

    override fun areContentsTheSame(other: Any): Boolean {
        return genre.name == (other as? GenreBinder)?.genre?.name
                && genre.songCount == (other as? GenreBinder)?.genre?.songCount
    }

    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<GenreBinder>(itemView) {

        private val titleTextView: TextView = itemView.findViewById(R.id.title)
        private val subtitleTextView: TextView = itemView.findViewById(R.id.subtitle)
        private val overflowButton: ImageButton = itemView.findViewById(R.id.overflowButton)

        init {
            itemView.setOnClickListener { viewBinder?.listener?.onGenreSelected(viewBinder!!.genre, this) }
            overflowButton.setOnClickListener { viewBinder?.listener?.onOverflowClicked(it, viewBinder!!.genre) }
        }

        override fun bind(viewBinder: GenreBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            titleTextView.text = viewBinder.genre.name
            if (viewBinder.genre.songCount == 0) {
                subtitleTextView.text = itemView.resources.getString(R.string.song_list_empty)
            } else {
                subtitleTextView.text = Phrase
                    .fromPlural(itemView.context, R.plurals.songsPlural, viewBinder.genre.songCount)
                    .put("count", viewBinder.genre.songCount)
                    .format()
            }
        }
    }
}