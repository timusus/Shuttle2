package com.simplecityapps.shuttle.ui.screens.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.screens.library.playlists.smart.SmartPlaylist
import com.simplecityapps.shuttle.ui.screens.library.songs.SongBinder

class RecentlyPlayedSectionBinder(val songs: List<Song>, val imageLoader: ArtworkImageLoader, val listener: SectionBinderListener) : ViewBinder {

    val playlist: SmartPlaylist = SmartPlaylist.RecentlyPlayed

    override fun createViewHolder(parent: ViewGroup): ViewBinder.ViewHolder<out ViewBinder> {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_recently_played_section, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.RecentlyPlayedSection
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RecentlyPlayedSectionBinder

        if (songs != other.songs) return false

        return true
    }

    override fun hashCode(): Int {
        return songs.hashCode()
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<RecentlyPlayedSectionBinder>(itemView) {

        private val headerContainer: View = itemView.findViewById(R.id.headerContainer)
        private val recyclerView: RecyclerView = itemView.findViewById(R.id.recyclerView)

        val adapter: RecyclerAdapter = RecyclerAdapter()

        init {
            headerContainer.setOnClickListener { viewBinder?.listener?.onHeaderClicked(viewBinder!!.playlist) }
        }

        override fun bind(viewBinder: RecentlyPlayedSectionBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            recyclerView.adapter = adapter
            adapter.setData(viewBinder.songs.map { song ->
                SongBinder(song, viewBinder.imageLoader, object : SongBinder.Listener {
                    override fun onSongClicked(song: Song) {
                        viewBinder.listener.onSongClicked(song, viewBinder.songs)
                    }

                    override fun onOverflowClicked(view: View, song: Song) {
                        viewBinder.listener.onOverflowClicked(view, song)
                    }
                })
            })
        }
    }
}