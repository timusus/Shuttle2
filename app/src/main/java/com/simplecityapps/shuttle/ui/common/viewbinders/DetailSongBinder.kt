package com.simplecityapps.shuttle.ui.common.viewbinders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
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

    override fun areContentsTheSame(other: Any): Boolean {
        return song.playbackPosition == (other as? Song)?.playbackPosition
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

        private val trackTextView: TextView = itemView.findViewById(R.id.trackTextView)
        private val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        private val durationTextView: TextView = itemView.findViewById(R.id.durationTextView)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)

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

            if ((viewBinder.song.type == Song.Type.Audiobook || viewBinder.song.type == Song.Type.Podcast) && viewBinder.song.playbackPosition != 0) {
                progressBar.progress = (((viewBinder.song.playbackPosition.toFloat() / viewBinder.song.duration) * 1000).toInt())
                progressBar.isVisible = true
            } else {
                progressBar.isVisible = false
            }
        }
    }
}