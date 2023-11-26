package com.simplecityapps.shuttle.ui.screens.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.ui.common.recyclerview.SpacesItemDecoration
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistBinder
import com.simplecityapps.shuttle.ui.screens.library.albumartists.GridAlbumArtistBinder
import kotlinx.coroutines.CoroutineScope

class HorizontalAlbumArtistListBinder(
    val albumArtists: List<com.simplecityapps.shuttle.model.AlbumArtist>,
    val imageLoader: ArtworkImageLoader,
    val scope: CoroutineScope,
    val listener: AlbumArtistBinder.Listener
) : ViewBinder {
    override fun createViewHolder(parent: ViewGroup): ViewBinder.ViewHolder<out ViewBinder> {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_horizontal_list, parent, false), scope)
    }

    override fun viewType(): Int {
        return ViewTypes.HorizontalAlbumArtistSection
    }

    override fun areContentsTheSame(other: Any): Boolean {
        return albumArtists == (other as? HorizontalAlbumArtistListBinder)?.albumArtists
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HorizontalAlbumArtistListBinder

        if (albumArtists != other.albumArtists) return false

        return true
    }

    override fun hashCode(): Int {
        return albumArtists.hashCode()
    }

    class ViewHolder(itemView: View, scope: CoroutineScope) : ViewBinder.ViewHolder<HorizontalAlbumArtistListBinder>(itemView) {
        private val recyclerView: RecyclerView = itemView.findViewById(R.id.recyclerView)

        val adapter: RecyclerAdapter = RecyclerAdapter(scope)

        init {
            recyclerView.addItemDecoration(SpacesItemDecoration(4))
        }

        override fun bind(
            viewBinder: HorizontalAlbumArtistListBinder,
            isPartial: Boolean
        ) {
            super.bind(viewBinder, isPartial)

            recyclerView.adapter = adapter
            adapter.update(
                viewBinder.albumArtists.map { albumArtist ->
                    GridAlbumArtistBinder(
                        albumArtist,
                        viewBinder.imageLoader,
                        listener = viewBinder.listener,
                        fixedWidthDp = 108
                    )
                }
            )
        }
    }
}
