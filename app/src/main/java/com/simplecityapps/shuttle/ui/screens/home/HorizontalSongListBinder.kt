package com.simplecityapps.shuttle.ui.screens.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.SpacesItemDecoration
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.screens.library.songs.GridSongBinder

class HorizontalSongListBinder(
    val title: String,
    val subtitle: String,
    val songs: List<Song>,
    val imageLoader: ArtworkImageLoader
) : ViewBinder {

    override fun createViewHolder(parent: ViewGroup): ViewBinder.ViewHolder<out ViewBinder> {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_horizontal_list, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.HorizontalSongSection
    }

    override fun areContentsTheSame(other: Any): Boolean {
        return songs == (other as? HorizontalSongListBinder)?.songs
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HorizontalSongListBinder

        if (title != other.title) return false
        if (subtitle != other.subtitle) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + subtitle.hashCode()
        return result
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<HorizontalSongListBinder>(itemView) {

        private val titleLabel: TextView = itemView.findViewById(R.id.titleLabel)
        private val subtitleLabel: TextView = itemView.findViewById(R.id.subtitleLabel)
        private val headerContainer: View = itemView.findViewById(R.id.headerContainer)
        private val recyclerView: RecyclerView = itemView.findViewById(R.id.recyclerView)

        val adapter: RecyclerAdapter = RecyclerAdapter()

        init {
            recyclerView.addItemDecoration(SpacesItemDecoration(4))
//            headerContainer.setOnClick Listener { viewBinder?.listener?.onHeaderClicked() }
        }

        override fun bind(viewBinder: HorizontalSongListBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            titleLabel.text = viewBinder.title
            subtitleLabel.text = viewBinder.subtitle

            recyclerView.adapter = adapter
            adapter.setData(viewBinder.songs.map { song ->
                GridSongBinder(song, viewBinder.imageLoader, object : GridSongBinder.Listener {
                    override fun onSongClicked(song: Song) {

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