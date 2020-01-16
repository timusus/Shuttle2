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
import com.simplecityapps.shuttle.ui.common.recyclerview.SpacesItemDecoration
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.screens.library.playlists.smart.SmartPlaylist
import com.simplecityapps.shuttle.ui.screens.library.songs.GridSongBinder

interface SectionBinderListener {
    fun onSongClicked(song: Song, songs: List<Song>)
    fun onOverflowClicked(view: View, song: Song)
    fun onHeaderClicked(playlist: SmartPlaylist)
}

class MostPlayedSectionBinder(val songs: List<Song>, val imageLoader: ArtworkImageLoader, val listener: SectionBinderListener) : ViewBinder {

    val playlist: SmartPlaylist = SmartPlaylist.MostPlayed

    override fun createViewHolder(parent: ViewGroup): ViewBinder.ViewHolder<out ViewBinder> {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_most_played_section, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.MostPlayedSection
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MostPlayedSectionBinder

        if (songs != other.songs) return false

        return true
    }

    override fun hashCode(): Int {
        return songs.hashCode()
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<MostPlayedSectionBinder>(itemView) {

        private val headerContainer: View = itemView.findViewById(R.id.headerContainer)
        private val recyclerView: RecyclerView = itemView.findViewById(R.id.recyclerView)

        val adapter: RecyclerAdapter = RecyclerAdapter()

        init {
            recyclerView.addItemDecoration(SpacesItemDecoration(4))
            headerContainer.setOnClickListener { viewBinder?.listener?.onHeaderClicked(viewBinder!!.playlist) }
        }

        override fun bind(viewBinder: MostPlayedSectionBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            recyclerView.adapter = adapter
            adapter.setData(viewBinder.songs.map { song ->
                GridSongBinder(song, viewBinder.imageLoader, object : GridSongBinder.Listener {
                    override fun onSongClicked(song: Song) {
                        viewBinder.listener.onSongClicked(song, viewBinder.songs)
                    }
                })
            })
        }

        override fun recycle() {
            adapter.dispose()
            super.recycle()
        }
    }
}