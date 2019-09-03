package com.simplecityapps.shuttle.ui.screens.library.playlists

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.common.utils.toHms

class PlaylistBinder(val playlist: Playlist, private val listener: Listener) : ViewBinder {

    interface Listener {
        fun onPlaylistSelected(playlist: Playlist, viewHolder: ViewHolder)
        fun onOverflowClicked(view: View, playlist: Playlist) {}
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_playlist, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.Playlist
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PlaylistBinder

        if (playlist != other.playlist) return false

        return true
    }

    override fun hashCode(): Int {
        return playlist.hashCode()
    }

    override fun areContentsTheSame(other: Any): Boolean {
        return playlist.name == (other as? PlaylistBinder)?.playlist?.name
                && playlist.songCount == (other as? PlaylistBinder)?.playlist?.songCount
    }

    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<PlaylistBinder>(itemView) {

        private val titleTextView: TextView = itemView.findViewById(R.id.title)
        private val subtitleTextView: TextView = itemView.findViewById(R.id.subtitle)
        private val overflowButton: ImageButton = itemView.findViewById(R.id.overflowButton)

        init {
            itemView.setOnClickListener { viewBinder?.listener?.onPlaylistSelected(viewBinder!!.playlist, this) }
            overflowButton.setOnClickListener { viewBinder?.listener?.onOverflowClicked(it, viewBinder!!.playlist) }
        }

        override fun bind(viewBinder: PlaylistBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            titleTextView.text = viewBinder.playlist.name
            if (viewBinder.playlist.songCount == 0) {
                subtitleTextView.text = "Playlist empty"
            } else {
                subtitleTextView.text = "${viewBinder.playlist.songCount} Song(s) • ${viewBinder.playlist.duration.toHms()}"
            }
        }
    }
}