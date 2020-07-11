package com.simplecityapps.shuttle.ui.screens.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.SpacesItemDecoration
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.screens.library.songs.GridAlbumArtistBinder
import kotlinx.coroutines.CoroutineScope

class HorizontalAlbumArtistListBinder(
    val title: String,
    val subtitle: String,
    val albumArtists: List<AlbumArtist>,
    val imageLoader: ArtworkImageLoader,
    val scope: CoroutineScope,
    val listener: GridAlbumArtistBinder.Listener? = null
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

        if (title != other.title) return false
        if (subtitle != other.subtitle) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + subtitle.hashCode()
        return result
    }


    class ViewHolder(itemView: View, scope: CoroutineScope) : ViewBinder.ViewHolder<HorizontalAlbumArtistListBinder>(itemView) {

        private val titleLabel: TextView = itemView.findViewById(R.id.titleLabel)
        private val subtitleLabel: TextView = itemView.findViewById(R.id.subtitleLabel)
        private val headerContainer: View = itemView.findViewById(R.id.headerContainer)
        private val recyclerView: RecyclerView = itemView.findViewById(R.id.recyclerView)

        val adapter: RecyclerAdapter = RecyclerAdapter(scope)

        init {
            recyclerView.addItemDecoration(SpacesItemDecoration(4))
//            headerContainer.setOnClickListener { viewBinder?.listener?.onHeaderClicked() }
        }

        override fun bind(viewBinder: HorizontalAlbumArtistListBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            titleLabel.text = viewBinder.title
            subtitleLabel.text = viewBinder.subtitle

            recyclerView.adapter = adapter
            adapter.update(viewBinder.albumArtists.map { albumArtist ->
                GridAlbumArtistBinder(albumArtist, viewBinder.imageLoader, listener = viewBinder.listener)
            })
        }

        override fun recycle() {

            super.recycle()
        }
    }
}