package com.simplecityapps.playback.chromecast

import android.os.Looper
import androidx.media3.cast.CastPlayer
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.android.gms.cast.MediaQueueItem
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.simplecityapps.playback.queue.queueEntry

/**
 * Stands in for Media3's Cast player: plays through the local player until [connect], then through [receiver] until
 * [disconnect], handing over as `CastPlayerImpl.updateActivePlayer` does: the transfer callback, then the new player
 * prepared (if the old one wasn't idle), then the old one stopped, and only then the new one active.
 */
class FakeCastPlayer(
    localPlayer: Player,
    private val receiver: Player,
    private val transferCallback: CastPlayer.TransferCallback
) : ForwardingSimpleBasePlayer(localPlayer) {
    private val local = localPlayer

    fun connect() = switchTo(receiver)

    fun disconnect() = switchTo(local)

    private fun switchTo(target: Player) {
        val source = player
        if (source === target) return
        transferCallback.transferState(source, target)
        if (source.playbackState != Player.STATE_IDLE) target.prepare()
        source.stop()
        setPlayer(target)
    }
}

/**
 * Stands in for Media3's `RemoteCastPlayer` and the receiver behind it. Items are sent through the [converter] as the
 * real player sends them, and reported back through it. What it's sent only shows in its playlist once the receiver
 * reports it ([deliver]), as the real one's timeline comes from the receiver's status, not from what it was sent. It
 * never reports [Player.STATE_ENDED]: a receiver that plays out its last item goes idle ([playOut]), and so does one
 * that's stopped.
 */
class FakeReceiver(private val converter: CastMediaItemConverter) : SimpleBasePlayer(Looper.getMainLooper()) {
    /** Every item sent, in the order sent: what the receiver was told to load. */
    val sentItems = mutableListOf<MediaQueueItem>()

    /** Whether the receiver went idle because its item played to the end. */
    var finished = false
        private set

    private class Item(val mediaItem: MediaItem) {
        val uid = Any()
    }

    private var items = listOf<Item>()
    private var index = 0
    private var positionMs = 0L
    private var playWhenReady = false
    private var playbackState = Player.STATE_IDLE
    private var repeatMode = Player.REPEAT_MODE_OFF
    private var playbackParameters = PlaybackParameters.DEFAULT
    private var discontinuity: Int? = null

    /** Changes sent that the receiver hasn't reported yet, in order. */
    private val undelivered = mutableListOf<() -> Unit>()

    /** The ids of the songs the receiver's playlist holds, in its order. */
    val songIds: List<Long> get() = List(mediaItemCount) { getMediaItemAt(it).queueEntry.song.id }

    /** Whether changes were sent that the receiver hasn't reported. */
    val hasUndelivered: Boolean get() = undelivered.isNotEmpty()

    /** Reports every change sent so far. */
    fun deliver() {
        val changes = undelivered.toList()
        undelivered.clear()
        changes.forEach { it() }
        invalidateState()
    }

    /** The receiver plays its current item to its end and moves on to the next, wrapping round under repeat-all. */
    fun playOnToNext() {
        val next = when {
            index < items.lastIndex -> index + 1
            repeatMode == Player.REPEAT_MODE_ALL -> 0
            else -> error("Nothing to play on to")
        }
        moveTo(next, Player.DISCONTINUITY_REASON_AUTO_TRANSITION)
    }

    /** The receiver is moved to its item at [index] by someone else: another sender, or its own controls. */
    fun skipTo(index: Int) = moveTo(index, Player.DISCONTINUITY_REASON_SEEK)

    /** The receiver plays its item on to [positionMs]. */
    fun playTo(positionMs: Long) {
        this.positionMs = positionMs
        invalidateState()
    }

    /** The receiver plays its last item to its end, and goes idle. */
    fun playOut() {
        finished = true
        playbackState = Player.STATE_IDLE
        invalidateState()
    }

    /** Another sender stops the receiver. */
    fun stopFromElsewhere() {
        finished = false
        playbackState = Player.STATE_IDLE
        invalidateState()
    }

    private fun moveTo(
        index: Int,
        reason: Int
    ) {
        this.index = index
        positionMs = 0
        finished = false
        playbackState = Player.STATE_READY
        discontinuity = reason
        invalidateState()
    }

    override fun getState(): State {
        val builder = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().remove(Player.COMMAND_SET_SHUFFLE_MODE).build())
            .setDeviceInfo(DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build())
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setRepeatMode(repeatMode)
            .setPlaybackParameters(playbackParameters)
            .setPlaylist(items.map { MediaItemData.Builder(it.uid).setMediaItem(it.mediaItem).build() })
            .setPlaybackState(if (items.isEmpty()) Player.STATE_IDLE else playbackState)
            .setContentPositionMs(positionMs)
        if (items.isNotEmpty()) builder.setCurrentMediaItemIndex(index)
        discontinuity?.let { reason -> builder.setPositionDiscontinuity(reason, positionMs) }
        discontinuity = null
        return builder.build()
    }

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<*> {
        val loaded = send(mediaItems)
        undelivered += {
            items = loaded
            index = startIndex.coerceAtLeast(0)
            positionMs = if (startPositionMs == C.TIME_UNSET) 0 else startPositionMs
            finished = false
            playbackState = Player.STATE_READY
        }
        return done()
    }

    override fun handleAddMediaItems(
        index: Int,
        mediaItems: List<MediaItem>
    ): ListenableFuture<*> {
        val added = send(mediaItems)
        undelivered += {
            items = items.toMutableList().apply { addAll(index.coerceAtMost(size), added) }
            if (this.index >= index && items.size > added.size) this.index += added.size
        }
        return done()
    }

    override fun handleRemoveMediaItems(
        fromIndex: Int,
        toIndex: Int
    ): ListenableFuture<*> {
        undelivered += {
            items = items.filterIndexed { i, _ -> i !in fromIndex until toIndex }
            index = when {
                index >= toIndex -> index - (toIndex - fromIndex)
                index >= fromIndex -> fromIndex.coerceAtMost(items.lastIndex).coerceAtLeast(0)
                else -> index
            }
        }
        return done()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int
    ): ListenableFuture<*> {
        if (mediaItemIndex != C.INDEX_UNSET) index = mediaItemIndex
        this.positionMs = if (positionMs == C.TIME_UNSET) 0 else positionMs
        finished = false
        if (items.isNotEmpty()) playbackState = Player.STATE_READY
        return done()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        this.playWhenReady = playWhenReady
        return done()
    }

    override fun handlePrepare(): ListenableFuture<*> = done()

    override fun handleStop(): ListenableFuture<*> {
        playbackState = Player.STATE_IDLE
        return done()
    }

    override fun handleRelease(): ListenableFuture<*> = done()

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        this.repeatMode = repeatMode
        return done()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        this.playbackParameters = playbackParameters
        return done()
    }

    /** Sends [mediaItems] as the real player does, and what the receiver will report back for them. */
    private fun send(mediaItems: List<MediaItem>): List<Item> = mediaItems.map { item ->
        val queueItem = converter.toMediaQueueItem(item)
        sentItems += queueItem
        Item(converter.toMediaItem(queueItem))
    }

    private fun done(): ListenableFuture<*> = Futures.immediateVoidFuture()
}
