package com.simplecityapps.shuttle.ui.screens.library.playlists

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.isVisible
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.SmartPlaylist
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes

class SmartPlaylistBinder(val playlist: SmartPlaylist, private val listener: Listener) : ViewBinder {

    interface Listener {
        fun onSmartPlaylistSelected(smartPlaylist: SmartPlaylist, viewHolder: ViewHolder)
        fun onOverflowClicked(view: View, playlist: SmartPlaylist) {}
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_smart_playlist, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.SmartPlaylist
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SmartPlaylistBinder

        if (playlist != other.playlist) return false

        return true
    }

    override fun hashCode(): Int {
        return playlist.hashCode()
    }

    override fun areContentsTheSame(other: Any): Boolean {
        return playlist.nameResId == (other as? SmartPlaylistBinder)?.playlist?.nameResId
    }

    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<SmartPlaylistBinder>(itemView) {

        private val titleTextView: TextView = itemView.findViewById(R.id.title)
        private val subtitleTextView: TextView = itemView.findViewById(R.id.subtitle)
        private val overflowButton: ImageButton = itemView.findViewById(R.id.overflowButton)

        init {
            itemView.setOnClickListener { viewBinder?.listener?.onSmartPlaylistSelected(viewBinder!!.playlist, this) }
            overflowButton.setOnClickListener { viewBinder?.listener?.onOverflowClicked(it, viewBinder!!.playlist) }
        }

        override fun bind(viewBinder: SmartPlaylistBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            titleTextView.setText(viewBinder.playlist.nameResId)
            subtitleTextView.isVisible = false
        }
    }
}