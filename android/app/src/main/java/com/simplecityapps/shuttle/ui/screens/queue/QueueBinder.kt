package com.simplecityapps.shuttle.ui.screens.queue

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackProgress
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.phrase.joinSafely
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionViewBinder
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.common.utils.dp
import com.simplecityapps.shuttle.ui.common.utils.toHms
import com.simplecityapps.shuttle.ui.common.view.PlayStateImageButton
import com.simplecityapps.shuttle.ui.common.view.ProgressView
import com.simplecityapps.shuttle.ui.common.view.increaseTouchableArea
import com.squareup.phrase.ListPhrase
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.launch

class QueueBinder(
    val queueItem: QueueItem,
    val imageLoader: ArtworkImageLoader,
    val playbackManager: PlaybackOperations,
    val listener: Listener
) : ViewBinder,
    SectionViewBinder {
    interface Listener {
        fun onQueueItemClicked(queueItem: QueueItem)

        fun onPlayPauseClicked()

        fun onStartDrag(viewHolder: ViewHolder)

        fun onLongPress(viewHolder: ViewHolder)
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_queue, parent, false))

    override fun viewType(): Int = ViewTypes.Queue

    override fun getSectionName(): String? = queueItem.song.name?.firstOrNull()?.toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QueueBinder) return false

        if (queueItem != other.queueItem) return false

        return true
    }

    override fun hashCode(): Int = queueItem.hashCode()

    override fun areContentsTheSame(other: Any): Boolean {
        (other as? QueueBinder)?.let {
            return queueItem.isCurrent == other.queueItem.isCurrent && queueItem.song == other.queueItem.song
        }

        return true
    }

    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<QueueBinder>(itemView) {
        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        private val tertiary: TextView = itemView.findViewById(R.id.tertiary)
        private val artworkImageView: ImageView = itemView.findViewById(R.id.artwork)
        private val progressView: ProgressView = itemView.findViewById(R.id.progressView)
        private val playStateImageButton: PlayStateImageButton = itemView.findViewById(R.id.playPauseButton)
        private val dragHandle: ImageView = itemView.findViewById(R.id.dragHandle)

        private var playbackJob: Job? = null

        private var renderedProgress: PlaybackProgress? = null

        private var renderedPlaybackState: PlaybackState? = null

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

        override fun bind(
            viewBinder: QueueBinder,
            isPartial: Boolean
        ) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.queueItem.song.name
            subtitle.text =
                ListPhrase.from(" • ")
                    .joinSafely(
                        items = listOf(viewBinder.queueItem.song.friendlyArtistName ?: viewBinder.queueItem.song.albumArtist, viewBinder.queueItem.song.album),
                        defaultValue = itemView.resources.getString(com.simplecityapps.core.R.string.unknown)
                    )
            tertiary.text = viewBinder.queueItem.song.duration.toHms("--:--")

            viewBinder.imageLoader.loadArtwork(
                imageView = artworkImageView,
                data = viewBinder.queueItem.song,
                options =
                    listOf(
                        ArtworkImageLoader.Options.RoundedCorners(8.dp),
                        ArtworkImageLoader.Options.Crossfade(200),
                        ArtworkImageLoader.Options.Placeholder(ResourcesCompat.getDrawable(itemView.resources, com.simplecityapps.core.R.drawable.ic_placeholder_song_rounded, itemView.context.theme)!!)
                    )
            )

            // Snapshotted before the live reads below, which are at least as new, so observePlayback()
            // applies any change made after this point.
            renderedProgress = viewBinder.playbackManager.progressFlow.value
            renderedPlaybackState = viewBinder.playbackManager.playbackStateFlow.value

            progressView.isVisible = viewBinder.queueItem.isCurrent
            progressView.setProgress((viewBinder.playbackManager.getProgress()?.toFloat() ?: 0f) / viewBinder.queueItem.song.duration.toFloat())
            playStateImageButton.state = viewBinder.playbackManager.playbackState()

            stopObservingPlayback()

            if (viewBinder.queueItem.isCurrent) {
                observePlayback()
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
                observePlayback()
            }
        }

        override fun onDetach() {
            stopObservingPlayback()
        }

        /**
         * Follows playback progress and state while the current item is attached. Each flow's replayed
         * value is skipped if it's the one last rendered ([bind]'s snapshot, or the last applied), so a
         * change made since then (including while detached) is still applied. Until the view is attached
         * there's no lifecycle to scope to; [onAttach] starts it then.
         */
        private fun observePlayback() {
            val viewBinder = viewBinder ?: return
            val lifecycleOwner = itemView.findViewTreeLifecycleOwner() ?: return
            stopObservingPlayback()
            playbackJob =
                lifecycleOwner.lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        viewBinder.playbackManager.progressFlow.dropWhile { it == renderedProgress }.collect { progress ->
                            renderedProgress = progress
                            progress?.let { progressView.setProgress((progress.position / progress.duration.toFloat())) }
                        }
                    }
                    viewBinder.playbackManager.playbackStateFlow.dropWhile { it == renderedPlaybackState }.collect { playbackState ->
                        renderedPlaybackState = playbackState
                        playStateImageButton.state = playbackState
                    }
                }
        }

        private fun stopObservingPlayback() {
            playbackJob?.cancel()
            playbackJob = null
        }
    }
}
