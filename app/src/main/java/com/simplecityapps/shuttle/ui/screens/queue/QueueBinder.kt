package com.simplecityapps.shuttle.ui.screens.queue

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.PlaybackWatcher
import com.simplecityapps.playback.PlaybackWatcherCallback
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionViewBinder
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.common.utils.toHms
import com.simplecityapps.shuttle.ui.common.view.PlayStateImageButton
import com.simplecityapps.shuttle.ui.common.view.ProgressView
import com.simplecityapps.shuttle.ui.common.view.increaseTouchableArea

class QueueBinder(
    val queueItem: QueueItem,
    var playbackState: PlaybackState,
    var progress: Float,
    val imageLoader: ArtworkImageLoader,
    val playbackManager: PlaybackManager,
    val playbackWatcher: PlaybackWatcher,
    val listener: Listener
) : ViewBinder,
    SectionViewBinder {

    interface Listener {
        fun onQueueItemClicked(queueItem: QueueItem)
        fun onPlayPauseClicked()
        fun onStartDrag(viewHolder: ViewHolder)
        fun onLongPress(viewHolder: ViewHolder)
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_queue, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.Queue
    }

    override fun getSectionName(): String? {
        return queueItem.song.name.firstOrNull().toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QueueBinder) return false

        if (queueItem != other.queueItem) return false

        return true
    }

    override fun hashCode(): Int {
        return queueItem.hashCode()
    }

    override fun areContentsTheSame(other: Any): Boolean {
        (other as? QueueBinder)?.let {
            return queueItem.isCurrent == other.queueItem.isCurrent
        }

        return true
    }


    class ViewHolder(itemView: View) :
        ViewBinder.ViewHolder<QueueBinder>(itemView),
        PlaybackWatcherCallback {

        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        private val tertiary: TextView = itemView.findViewById(R.id.tertiary)
        private val artworkImageView: ImageView = itemView.findViewById(R.id.artwork)
        private val progressView: ProgressView = itemView.findViewById(R.id.progressView)
        private val playStateImageButton: PlayStateImageButton = itemView.findViewById(R.id.playPauseButton)
        private val dragHandle: ImageView = itemView.findViewById(R.id.dragHandle)

        init {
            itemView.setOnClickListener {
                viewBinder?.listener?.onQueueItemClicked(viewBinder!!.queueItem)
            }

            itemView.setOnLongClickListener {
                viewBinder?.listener?.onLongPress(this)
                true
            }

            playStateImageButton.increaseTouchableArea(8)
            playStateImageButton.setOnClickListener {
                viewBinder?.listener?.onPlayPauseClicked()
            }

            dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    viewBinder?.listener?.onStartDrag(this)
                }
                true
            }
        }

        override fun bind(viewBinder: QueueBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.queueItem.song.name
            subtitle.text = "${viewBinder.queueItem.song.albumArtist} • ${viewBinder.queueItem.song.album}"
            tertiary.text = viewBinder.queueItem.song.duration.toHms("--:--")

            viewBinder.imageLoader.loadArtwork(
                artworkImageView,
                viewBinder.queueItem.song,
                listOf(
                    ArtworkImageLoader.Options.RoundedCorners(16),
                    ArtworkImageLoader.Options.Crossfade(200),
                    ArtworkImageLoader.Options.Placeholder(R.drawable.ic_placeholder_song_rounded)
                )
            )

            progressView.isVisible = viewBinder.queueItem.isCurrent
            progressView.setProgress((viewBinder.playbackManager.getProgress()?.toFloat() ?: 0f) / viewBinder.queueItem.song.duration.toFloat())
            playStateImageButton.state = viewBinder.playbackManager.playbackState()

            viewBinder.playbackWatcher.removeCallback(this)

            if (viewBinder.queueItem.isCurrent) {
                viewBinder.playbackWatcher.addCallback(this)
                itemView.isActivated = true
                artworkImageView.isInvisible = true
                playStateImageButton.isVisible = true
            } else {
                itemView.isActivated = false
                artworkImageView.isVisible = true
                playStateImageButton.isVisible = false
            }
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(artworkImageView)
        }

        override fun onAttach() {
            if (viewBinder?.queueItem?.isCurrent == true) {
                viewBinder?.playbackWatcher?.addCallback(this)
            }
        }

        override fun onDetach() {
            viewBinder?.playbackWatcher?.removeCallback(this)
        }

        override fun onProgressChanged(position: Int, duration: Int, fromUser: Boolean) {
            progressView.setProgress((position / duration.toFloat()))
        }

        override fun onPlaybackStateChanged(playbackState: PlaybackState) {
            playStateImageButton.state = playbackState
        }
    }
}
