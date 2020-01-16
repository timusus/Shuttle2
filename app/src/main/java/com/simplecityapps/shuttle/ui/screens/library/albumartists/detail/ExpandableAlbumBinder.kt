package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionViewBinder
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.common.view.increaseTouchableArea
import com.simplecityapps.shuttle.ui.common.viewbinders.DetailSongBinder
import com.simplecityapps.shuttle.ui.common.viewbinders.DiscNumberBinder

class ExpandableAlbumBinder(
    val album: Album,
    val songs: List<Song>,
    val imageLoader: ArtworkImageLoader,
    val expanded: Boolean = false,
    val listener: Listener?
) : ViewBinder,
    SectionViewBinder {

    interface Listener {
        fun onSongClicked(song: Song, songs: List<Song>)
        fun onArtworkClicked(album: Album, viewHolder: ViewHolder)
        fun onItemClicked(position: Int, expanded: Boolean)
        fun onOverflowClicked(view: View, album: Album) {}
        fun onOverflowClicked(view: View, song: Song) {}
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_album_expandable, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.ExpandableAlbum
    }

    override fun getSectionName(): String? {
        return album.sortKey?.firstOrNull().toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExpandableAlbumBinder) return false

        if (album != other.album) return false

        return true
    }

    override fun hashCode(): Int {
        return album.hashCode()
    }

    override fun areContentsTheSame(other: Any): Boolean {
        return (other as? ExpandableAlbumBinder)?.let {
            album.name == other.album.name
                    && album.albumArtistName == other.album.albumArtistName
                    && expanded == other.expanded
        } ?: false
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<ExpandableAlbumBinder>(itemView) {

        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val overflowButton: ImageButton = itemView.findViewById(R.id.overflowButton)
        private val recyclerView: RecyclerView = itemView.findViewById(R.id.recyclerView)
        private val adapter: RecyclerAdapter = RecyclerAdapter()

        init {
            itemView.setOnClickListener {
                viewBinder?.listener?.onItemClicked(adapterPosition, viewBinder!!.expanded)
            }
            imageView.increaseTouchableArea(8)
            imageView.setOnClickListener { viewBinder?.listener?.onArtworkClicked(viewBinder!!.album, this) }
            overflowButton.setOnClickListener { view -> viewBinder?.listener?.onOverflowClicked(view, viewBinder!!.album) }
        }

        override fun bind(viewBinder: ExpandableAlbumBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            recyclerView.visibility = if (viewBinder.expanded) View.VISIBLE else View.GONE
            itemView.isActivated = viewBinder.expanded

            title.text = viewBinder.album.name
            subtitle.text = "${viewBinder.album.year.yearToString()} • ${viewBinder.album.songCount} Songs"

            viewBinder.imageLoader.loadArtwork(
                imageView, viewBinder.album,
                ArtworkImageLoader.Options.RoundedCorners(16),
                ArtworkImageLoader.Options.Crossfade(200)
            )

            imageView.transitionName = "album_${viewBinder.album.name}"

            recyclerView.adapter = adapter

            val discSongsMap = viewBinder.songs.groupBy { song -> song.disc }.toSortedMap()
            adapter.setData(discSongsMap.flatMap { entry ->
                val viewBinders = mutableListOf<ViewBinder>()
                if (discSongsMap.size > 1) {
                    viewBinders.add(DiscNumberBinder(entry.key))
                }
                viewBinders.addAll(entry.value.map { song -> DetailSongBinder(song, songBinderListener) })
                viewBinders
            })
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
            adapter.dispose()
        }

        private val songBinderListener = object : DetailSongBinder.Listener {

            override fun onSongClicked(song: Song) {
                viewBinder?.listener?.onSongClicked(song, viewBinder!!.songs)
            }

            override fun onOverflowClicked(view: View, song: Song) {
                viewBinder?.listener?.onOverflowClicked(view, song)
            }
        }


        // Extensions

        private fun Int.yearToString(): String {
            if (this == 0) return "Year Unknown"
            return this.toString()
        }
    }
}

fun ExpandableAlbumBinder.clone(expanded: Boolean): ExpandableAlbumBinder {
    return ExpandableAlbumBinder(album, songs, imageLoader, expanded, listener)
}