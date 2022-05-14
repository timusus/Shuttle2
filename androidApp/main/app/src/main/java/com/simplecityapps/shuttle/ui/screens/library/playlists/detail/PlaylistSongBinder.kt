package com.simplecityapps.shuttle.ui.screens.library.playlists.detail

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.phrase.joinSafely
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionViewBinder
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.common.utils.dp
import com.simplecityapps.shuttle.ui.screens.home.search.SongJaroSimilarity
import com.squareup.phrase.ListPhrase

open class PlaylistSongBinder(
    val playlistSong: com.simplecityapps.shuttle.model.PlaylistSong,
    val imageLoader: ArtworkImageLoader,
    val listener: Listener,
    val showDragHandle: Boolean = false,
    val jaroSimilarity: SongJaroSimilarity? = null
) : ViewBinder,
    SectionViewBinder {

    var selected: Boolean = false

    interface Listener {
        fun onPlaylistSongClicked(index: Int, playlistSong: com.simplecityapps.shuttle.model.PlaylistSong)
        fun onPlaylistSongLongClicked(holder: ViewHolder, playlistSong: com.simplecityapps.shuttle.model.PlaylistSong) {}
        fun onStartDrag(viewHolder: ViewHolder) {}
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_playlist_song, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.Song
    }

    override fun getSectionName(): String? {
        return playlistSong.song.name?.firstOrNull()?.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlaylistSongBinder) return false

        if (playlistSong.id != other.playlistSong.id) return false

        return true
    }

    override fun hashCode(): Int {
        return playlistSong.id.hashCode()
    }

    override fun areContentsTheSame(other: Any): Boolean {
        return other is PlaylistSongBinder &&
            playlistSong.song.name == other.playlistSong.song.name &&
            playlistSong.song.albumArtist == other.playlistSong.song.albumArtist &&
            playlistSong.song.artists == other.playlistSong.song.artists &&
            playlistSong.song.album == other.playlistSong.song.album &&
            playlistSong.song.date == other.playlistSong.song.date &&
            playlistSong.song.track == other.playlistSong.song.track &&
            playlistSong.song.disc == other.playlistSong.song.disc &&
            playlistSong.song.playCount == other.playlistSong.song.playCount &&
            selected == other.selected &&
            showDragHandle == other.showDragHandle &&
            jaroSimilarity == other.jaroSimilarity
    }

    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<PlaylistSongBinder>(itemView) {

        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val checkImageView: ImageView = itemView.findViewById(R.id.checkImageView)
        private val dragHandle: ImageView = itemView.findViewById(R.id.dragHandle)

        init {
            itemView.setOnClickListener {
                viewBinder?.listener?.onPlaylistSongClicked(adapterPosition, viewBinder!!.playlistSong)
            }
            itemView.setOnLongClickListener {
                viewBinder?.listener?.onPlaylistSongLongClicked(this, viewBinder!!.playlistSong)
                true
            }

            dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    viewBinder?.listener?.onStartDrag(this)
                }
                true
            }
        }

        override fun bind(viewBinder: PlaylistSongBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.playlistSong.song.name
            subtitle.text = ListPhrase
                .from(" • ")
                .joinSafely(
                    listOf(
                        viewBinder.playlistSong.song.friendlyArtistName ?: viewBinder.playlistSong.song.albumArtist,
                        viewBinder.playlistSong.song.album,
                        viewBinder.playlistSong.song.date?.year?.toString()
                    )
                )

            val options = mutableListOf(
                ArtworkImageLoader.Options.RoundedCorners(8.dp),
                ArtworkImageLoader.Options.Crossfade(200),
                ArtworkImageLoader.Options.Placeholder(ResourcesCompat.getDrawable(itemView.resources, R.drawable.ic_placeholder_song_rounded, itemView.context.theme)!!)
            )

            viewBinder.imageLoader.loadArtwork(
                imageView,
                viewBinder.playlistSong.song,
                options
            )

            checkImageView.isVisible = viewBinder.selected

            dragHandle.isVisible = viewBinder.showDragHandle
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
        }
    }
}
