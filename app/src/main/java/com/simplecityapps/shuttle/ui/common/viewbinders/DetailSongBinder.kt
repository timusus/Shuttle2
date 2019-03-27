package com.simplecityapps.shuttle.ui.common.viewbinders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.common.toHms

class DetailSongBinder(
    val song: Song,
    val listener: Listener? = null
) : ViewBinder {

    interface Listener {
        fun onSongClicked(song: Song)
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_detail_song, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.DetailSong
    }

    override fun sectionName(): String? {
        return song.name.firstOrNull().toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DetailSongBinder) return false

        if (song != other.song) return false

        return true
    }

    override fun hashCode(): Int {
        return song.hashCode()
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<DetailSongBinder>(itemView) {

        private val trackTextView = itemView.findViewById<TextView>(R.id.trackTextView)
        private val titleTextView = itemView.findViewById<TextView>(R.id.titleTextView)
        private val durationTextView = itemView.findViewById<TextView>(R.id.durationTextView)

        init {
            itemView.setOnClickListener {
                viewBinder?.listener?.onSongClicked(viewBinder!!.song)
            }
        }

        override fun bind(viewBinder: DetailSongBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            trackTextView.text = (adapterPosition + 1).toString()
            titleTextView.text = viewBinder.song.name
            durationTextView.text = viewBinder.song.duration.toHms()
        }
    }
}