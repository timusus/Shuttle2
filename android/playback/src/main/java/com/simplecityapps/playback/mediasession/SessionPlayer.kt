package com.simplecityapps.playback.mediasession

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.playback.queue.queueEntryOrNull
import com.simplecityapps.playback.queue.toRepeatMode
import java.util.IdentityHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The player the media session publishes: the app's own [Player], with every command a controller (the notification,
 * Android Auto, a Bluetooth headset, Assistant) sends routed through [PlaybackOperations] and [QueueOperations], the
 * way the app's own screens send them. State passes straight through; nothing is copied.
 *
 * The commands can't go to the player directly: playing takes audio focus and restores the saved position, skipping
 * honours S2's own repeat and restart-the-song rules, shuffling reorders the queue through S2's shuffle order, and a
 * new queue is built from songs so each item carries its [com.simplecityapps.playback.queue.QueueEntry] (the tag
 * replay gain and the queue read) and is written to the player that owns the queue while casting.
 *
 * Runs on the main thread, where the session calls it.
 */
class SessionPlayer(
    player: Player,
    private val playbackOperations: PlaybackOperations,
    private val queueOperations: QueueOperations,
    private val scope: CoroutineScope,
    private val classicalDisplay: StateFlow<Boolean> = MutableStateFlow(false)
) : ForwardingPlayer(player) {
    // The session reads the metadata on every state update, so the queue scan is done once per queue snapshot and song.
    private var cachedQueue: List<QueueItem>? = null
    private var cachedUid: Long? = null
    private var cachedText: ClassicalText? = null

    // ForwardingPlayer hands listeners the inner player's metadata; these hand them ours.
    private val listeners = IdentityHashMap<Player.Listener, Player.Listener>()

    /** Reworded for a movement of a work while Classical display is on. */
    override fun getMediaMetadata(): MediaMetadata {
        val metadata = super.getMediaMetadata()
        if (!classicalDisplay.value) return metadata
        val uid = currentMediaItem?.queueEntryOrNull?.uid ?: return metadata
        val text = classicalText(uid) ?: return metadata
        return metadata.withClassicalText(text)
    }

    private fun classicalText(uid: Long): ClassicalText? {
        val queue = queueOperations.getQueue()
        if (queue !== cachedQueue || uid != cachedUid) {
            val index = queue.indexOfFirst { it.uid == uid }
            cachedText = if (index == -1) null else classicalText(queue, index)
            cachedQueue = queue
            cachedUid = uid
        }
        return cachedText
    }

    init {
        // The session only re-reads the metadata when the song changes; the setting can change under a playing one.
        scope.launch { classicalDisplay.drop(1).collect { refreshMetadata() } }
    }

    private fun refreshMetadata() {
        cachedQueue = null
        val metadata = mediaMetadata
        val added = synchronized(listeners) { listeners.keys.toList() }
        added.forEach { it.onMediaMetadataChanged(metadata) }
    }

    override fun addListener(listener: Player.Listener) {
        val wrapper = synchronized(listeners) { listeners.getOrPut(listener) { MetadataListener(listener) } }
        super.addListener(wrapper)
    }

    override fun removeListener(listener: Player.Listener) {
        val wrapper = synchronized(listeners) { listeners.remove(listener) } ?: listener
        super.removeListener(wrapper)
    }

    // Written out because Kotlin's `by` skips the Java default methods of Player.Listener, which is every one of them.
    // Modelled on ForwardingPlayer.ForwardingListener, except that metadata is ours.
    @Suppress("DEPRECATION")
    private inner class MetadataListener(private val delegate: Player.Listener) : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = delegate.onEvents(player, events)

        override fun onTimelineChanged(timeline: Timeline, reason: Int) = delegate.onTimelineChanged(timeline, reason)

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = delegate.onMediaItemTransition(mediaItem, reason)

        override fun onTracksChanged(tracks: Tracks) = delegate.onTracksChanged(tracks)

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = delegate.onMediaMetadataChanged(this@SessionPlayer.mediaMetadata)

        override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) = delegate.onPlaylistMetadataChanged(mediaMetadata)

        override fun onIsLoadingChanged(isLoading: Boolean) = delegate.onIsLoadingChanged(isLoading)

        override fun onLoadingChanged(isLoading: Boolean) = delegate.onLoadingChanged(isLoading)

        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) = delegate.onAvailableCommandsChanged(availableCommands)

        override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) = delegate.onTrackSelectionParametersChanged(parameters)

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) = delegate.onPlayerStateChanged(playWhenReady, playbackState)

        override fun onPlaybackStateChanged(playbackState: Int) = delegate.onPlaybackStateChanged(playbackState)

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = delegate.onPlayWhenReadyChanged(playWhenReady, reason)

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) = delegate.onPlaybackSuppressionReasonChanged(playbackSuppressionReason)

        override fun onIsPlayingChanged(isPlaying: Boolean) = delegate.onIsPlayingChanged(isPlaying)

        override fun onRepeatModeChanged(repeatMode: Int) = delegate.onRepeatModeChanged(repeatMode)

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = delegate.onShuffleModeEnabledChanged(shuffleModeEnabled)

        override fun onPlayerError(error: PlaybackException) = delegate.onPlayerError(error)

        override fun onPlayerErrorChanged(error: PlaybackException?) = delegate.onPlayerErrorChanged(error)

        override fun onPositionDiscontinuity(reason: Int) = delegate.onPositionDiscontinuity(reason)

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) = delegate.onPositionDiscontinuity(oldPosition, newPosition, reason)

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = delegate.onPlaybackParametersChanged(playbackParameters)

        override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) = delegate.onSeekBackIncrementChanged(seekBackIncrementMs)

        override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) = delegate.onSeekForwardIncrementChanged(seekForwardIncrementMs)

        override fun onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs: Long) = delegate.onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs)

        override fun onAudioSessionIdChanged(audioSessionId: Int) = delegate.onAudioSessionIdChanged(audioSessionId)

        override fun onAudioAttributesChanged(audioAttributes: AudioAttributes) = delegate.onAudioAttributesChanged(audioAttributes)

        override fun onVolumeChanged(volume: Float) = delegate.onVolumeChanged(volume)

        override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) = delegate.onSkipSilenceEnabledChanged(skipSilenceEnabled)

        override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) = delegate.onDeviceInfoChanged(deviceInfo)

        override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) = delegate.onDeviceVolumeChanged(volume, muted)

        override fun onVideoSizeChanged(videoSize: VideoSize) = delegate.onVideoSizeChanged(videoSize)

        override fun onSurfaceSizeChanged(width: Int, height: Int) = delegate.onSurfaceSizeChanged(width, height)

        override fun onRenderedFirstFrame() = delegate.onRenderedFirstFrame()

        override fun onCues(cues: List<Cue>) = delegate.onCues(cues)

        override fun onCues(cueGroup: CueGroup) = delegate.onCues(cueGroup)

        override fun onMetadata(metadata: Metadata) = delegate.onMetadata(metadata)

        override fun equals(other: Any?): Boolean = this === other || (other is MetadataListener && delegate == other.delegate)

        override fun hashCode(): Int = delegate.hashCode()
    }

    override fun play() {
        playbackOperations.play()
    }

    override fun pause() {
        playbackOperations.pause()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) playbackOperations.play() else playbackOperations.pause()
    }

    override fun stop() {
        playbackOperations.pause()
    }

    // Playing prepares the player, at the saved position (see PlaybackOperations.play); preparing it first would start
    // it from wherever the player happens to be.
    override fun prepare() {}

    override fun seekToNext() {
        playbackOperations.skipToNext(ignoreRepeat = true)
    }

    override fun seekToNextMediaItem() {
        playbackOperations.skipToNext(ignoreRepeat = true)
    }

    override fun seekToPrevious() {
        playbackOperations.skipToPrev()
    }

    override fun seekToPreviousMediaItem() {
        playbackOperations.skipToPrev(force = true)
    }

    override fun seekTo(positionMs: Long) {
        playbackOperations.seekTo(positionMs.toInt())
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (mediaItemIndex == currentMediaItemIndex) {
            if (positionMs != C.TIME_UNSET) playbackOperations.seekTo(positionMs.toInt())
            return
        }
        // The session counts in playlist order; the queue counts in the order it's presented, which shuffle changes.
        val uid = getMediaItemAt(mediaItemIndex).queueEntryOrNull?.uid ?: return
        val position = queueOperations.getQueue().indexOfFirst { item -> item.uid == uid }
        if (position != -1) playbackOperations.skipTo(position)
    }

    override fun seekToDefaultPosition() {
        playbackOperations.seekTo(0)
    }

    override fun seekToDefaultPosition(mediaItemIndex: Int) {
        seekTo(mediaItemIndex, 0)
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        scope.launch {
            queueOperations.setShuffleMode(if (shuffleModeEnabled) ShuffleMode.On else ShuffleMode.Off, reshuffle = true)
        }
    }

    override fun setRepeatMode(repeatMode: Int) {
        queueOperations.setRepeatMode(repeatMode.toRepeatMode())
    }

    override fun setMediaItems(mediaItems: List<MediaItem>) {
        setMediaItems(mediaItems, 0, C.TIME_UNSET)
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
        setMediaItems(mediaItems, if (resetPosition) 0 else currentMediaItemIndex, C.TIME_UNSET)
    }

    override fun setMediaItem(mediaItem: MediaItem) {
        setMediaItems(listOf(mediaItem), 0, C.TIME_UNSET)
    }

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
        setMediaItems(listOf(mediaItem), 0, startPositionMs)
    }

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
        setMediaItems(listOf(mediaItem), resetPosition)
    }

    /**
     * The session's callback has already set the queue for a request to play something (see [SessionCallback]), and
     * hands back the player's own items, so those are left alone. Any other items are songs to set as a new queue.
     */
    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        if (mediaItems.uids() == currentMediaItems().uids()) {
            if (startIndex != C.INDEX_UNSET && startIndex != currentMediaItemIndex) seekTo(startIndex, startPositionMs)
            return
        }
        val songs = mediaItems.mapNotNull { item -> item.queueEntryOrNull?.song }
        if (songs.size != mediaItems.size) {
            Timber.w("Ignoring ${mediaItems.size} media items: not all of them are songs")
            return
        }
        scope.launch {
            if (queueOperations.setQueue(songs, position = startIndex.coerceIn(0, songs.lastIndex))) {
                playbackOperations.load(seekPosition = startPositionMs.takeIf { it != C.TIME_UNSET }?.toInt()) { result ->
                    result.onFailure { error ->
                        if (error is CancellationException) Timber.d("Load of the queue a controller set was replaced by a later one") else Timber.e(error, "Failed to load the queue a controller set")
                    }
                }
            }
        }
    }

    override fun addMediaItem(mediaItem: MediaItem) {
        addMediaItems(mediaItemCount, listOf(mediaItem))
    }

    override fun addMediaItem(index: Int, mediaItem: MediaItem) {
        addMediaItems(index, listOf(mediaItem))
    }

    override fun addMediaItems(mediaItems: List<MediaItem>) {
        addMediaItems(mediaItemCount, mediaItems)
    }

    /** Adds songs after the current item when asked to put them there, otherwise at the end of the queue. */
    override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) {
        val songs = mediaItems.mapNotNull { item -> item.queueEntryOrNull?.song }
        if (songs.isEmpty()) return
        scope.launch {
            if (mediaItemCount > 0 && index == currentMediaItemIndex + 1) {
                queueOperations.addToNext(songs)
            } else {
                queueOperations.addToQueue(songs)
            }
        }
    }

    override fun removeMediaItem(index: Int) {
        removeMediaItems(index, index + 1)
    }

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        val uids = (fromIndex until toIndex.coerceAtMost(mediaItemCount)).mapNotNull { index -> getMediaItemAt(index).queueEntryOrNull?.uid }.toSet()
        val items = queueOperations.getQueue().filter { item -> item.uid in uids }
        items.forEach(playbackOperations::removeQueueItem)
    }

    /** Moves an item, where the queue's presented order matches the playlist's: with shuffle off. */
    override fun moveMediaItem(currentIndex: Int, newIndex: Int) {
        if (shuffleModeEnabled) {
            Timber.w("Ignoring a controller's move while shuffle is on")
            return
        }
        playbackOperations.moveQueueItem(currentIndex, newIndex)
    }

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
        if (toIndex - fromIndex == 1) moveMediaItem(fromIndex, newIndex) else Timber.w("Ignoring a controller's move of ${toIndex - fromIndex} items")
    }

    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {
        Timber.w("Ignoring a controller's replace of item $index")
    }

    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: List<MediaItem>) {
        Timber.w("Ignoring a controller's replace of items $fromIndex until $toIndex")
    }

    override fun clearMediaItems() {
        playbackOperations.clearQueue()
    }

    private fun currentMediaItems(): List<MediaItem> = List(mediaItemCount, ::getMediaItemAt)

    private fun List<MediaItem>.uids(): List<Long?> = map { item -> item.queueEntryOrNull?.uid }
}
