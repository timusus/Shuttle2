package com.simplecityapps.playback.dsp.crossfade

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.ClippingMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.TimelineWithUpdatedMediaItem
import androidx.media3.exoplayer.source.WrappingMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.simplecityapps.playback.dsp.crossfade.CrossfadePlan.Next
import com.simplecityapps.playback.queue.QueueEntry
import com.simplecityapps.playback.queue.queueEntryOrNull

/**
 * Where [entry]'s item is clipped to end with a crossfade of [crossfadeMs], or null when it plays in full: crossfade
 * is off, or the song is too short to lose that much at each end.
 */
internal fun crossfadeClipEndMs(
    entry: QueueEntry,
    crossfadeMs: Long
): Long? {
    val durationMs = entry.song.duration.toLong()
    return if (crossfadeMs > 0 && durationMs >= 2 * crossfadeMs) durationMs - crossfadeMs else null
}

/**
 * Builds each queue entry's source so [Crossfade] can clip it, and unclip it, in place: a [ClippingMediaSource] that
 * clips in the media period, applying the item's [MediaItem.clippingConfiguration] and taking a new one through
 * [ExoPlayer.replaceMediaItem] without re-preparing. The delegate gets the item unclipped, since this source clips it.
 * Items are queued unclipped; [Crossfade] clips one only once its tail is decoded.
 */
class CrossfadeClippingMediaSourceFactory(
    private val delegate: MediaSource.Factory
) : MediaSource.Factory {
    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        if (mediaItem.queueEntryOrNull == null) return delegate.createMediaSource(mediaItem)
        val unclipped = mediaItem.buildUpon().setClippingConfiguration(MediaItem.ClippingConfiguration.UNSET).build()
        val clipping =
            ClippingMediaSource.Builder(delegate.createMediaSource(unclipped))
                .setClippingConfiguration(mediaItem.clippingConfiguration)
                .setEnableClippingInMediaPeriod(true)
                .build()
        return UpdatedItemMediaSource(clipping, mediaItem)
    }

    override fun getSupportedTypes(): IntArray = delegate.supportedTypes

    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory = apply { delegate.setDrmSessionManagerProvider(drmSessionManagerProvider) }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory = apply { delegate.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy) }
}

/**
 * Shows the latest item given to [updateMediaItem] in the timeline. A [ClippingMediaSource] refreshes its timeline
 * from its child's, which keeps the item the child was prepared with, so without this an item updated in place (a
 * song renamed by a library update, or re-clipped) would revert to the old one.
 */
private class UpdatedItemMediaSource(
    source: MediaSource,
    private var item: MediaItem
) : WrappingMediaSource(source) {
    override fun getMediaItem(): MediaItem = item

    override fun canUpdateMediaItem(mediaItem: MediaItem): Boolean = mediaSource.canUpdateMediaItem(mediaItem)

    override fun updateMediaItem(mediaItem: MediaItem) {
        item = mediaItem
        mediaSource.updateMediaItem(mediaItem)
    }

    override fun onChildSourceInfoRefreshed(newTimeline: Timeline) {
        refreshSourceInfo(TimelineWithUpdatedMediaItem.create(newTimeline, item))
    }
}

/**
 * How far ahead of the player's position a playing item's clip end must be for [Crossfade] to move it: the audio sink
 * runs up to 750 ms ahead of the position, and the renderer reads a little ahead of the sink.
 */
private const val CLIP_CHANGE_MARGIN_MS = 3_000L

/**
 * Keeps [mixer]'s plans up to date as [player] plays: decodes the tails of the current and next entries (one at a time,
 * the current first) and says what to do with each when its entry ends. It crossfades only into the entry that
 * plays next on its own; not on repeat-one, not between songs of the same album (they may be gapless), and not into a
 * song too short to be clipped. The mixer drops the tail on a skip or seek, so those cut hard.
 *
 * An entry's item is clipped (see [CrossfadeClippingMediaSourceFactory]) only once its tail is ready, so an entry whose
 * tail can't be decoded (a stream that can't seek, a decode error) plays whole; and it's unclipped again when the tail
 * is dropped (the entry leaves the current and next, or the crossfade length changes). A playing item's clip only
 * moves while its end is [CLIP_CHANGE_MARGIN_MS] ahead: past that, it keeps the clip and tail it has, or plays whole.
 */
class Crossfade(
    private val player: ExoPlayer,
    private val mixer: CrossfadeMixer,
    private val decoder: TailDecoder,
    private val crossfadeMs: () -> Long
) : Player.Listener {
    /** The tails decoded for the current and next entries, which may not be applied yet. */
    private val tails = mutableMapOf<Long, Decoded>()

    /**
     * The clips applied to entries' items, by uid, each with the tail it was cut for and the entry it was applied to:
     * an entry replaced since (a changed song) comes back unclipped.
     */
    private val clips = mutableMapOf<Long, Decoded>()

    /** Entries whose tail couldn't be decoded, which aren't tried again. */
    private val undecodable = mutableSetOf<Long>()

    private var decoding: Long? = null

    fun attach() {
        player.addListener(this)
        update()
    }

    fun release() {
        player.removeListener(this)
        decoder.cancel()
        mixer.plans = emptyMap()
    }

    override fun onEvents(
        player: Player,
        events: Player.Events
    ) {
        if (events.containsAny(
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_REPEAT_MODE_CHANGED,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED
            )
        ) {
            update()
        }
    }

    private fun update() {
        val crossfadeMs = crossfadeMs()
        // The current entry and the next (the same one, on repeat with one item).
        val currentIndex = player.currentMediaItemIndex
        val wanted =
            listOf(currentIndex, followingIndex(currentIndex)).distinct().mapNotNull { index ->
                val entry = mediaItemAt(index)?.queueEntryOrNull ?: return@mapNotNull null
                val clipEndMs = crossfadeClipEndMs(entry, crossfadeMs) ?: return@mapNotNull null
                Wanted(index, entry, clipEndMs)
            }
        // Keep only the tails still wanted, decoded for the current length.
        tails.entries.retainAll { (uid, decoded) -> wanted.any { it.entry.uid == uid && it.entry == decoded.entry && decoded.tail.clipEndUs == it.clipEndMs * 1000 } }
        if (decoding != null && wanted.none { it.entry.uid == decoding }) {
            decoder.cancel()
            decoding = null
        }

        // Clip each wanted entry to its tail once it's ready, and unclip the rest, where the clip can still move.
        val changes = mutableListOf<Pair<Int, Decoded?>>()
        for (want in wanted) {
            val tail = tails[want.entry.uid]
            val clip = clips[want.entry.uid]?.takeIf { it.entry == want.entry }
            if (clip !== tail && canMoveClip(want.index, clip, tail)) changes += want.index to tail
        }
        for ((uid, clip) in clips.toList()) {
            if (wanted.any { it.entry.uid == uid }) continue
            val index = indexOf(uid)
            when {
                // Gone, or replaced by an unclipped item for a changed song.
                index == null || mediaItemAt(index)?.queueEntryOrNull != clip.entry -> clips -= uid

                canMoveClip(index, clip, null) -> changes += index to null
            }
        }
        // Entries replaced since they were clipped play unclipped.
        wanted.forEach { want -> if (clips[want.entry.uid]?.let { it.entry != want.entry } == true) clips -= want.entry.uid }
        for ((index, decoded) in changes) {
            val uid = checkNotNull(mediaItemAt(index)?.queueEntryOrNull).uid
            if (decoded != null) clips[uid] = decoded else clips -= uid
        }

        // A plan for each clipped entry; set before the clips change, so the mixer knows a tail before its item is cut.
        mixer.plans = clips.mapNotNull { (uid, clip) -> indexOf(uid)?.let { index -> uid to CrossfadePlan(clip.tail, next(clip.entry, mediaItemAt(followingIndex(index))?.queueEntryOrNull, crossfadeMs)) } }.toMap()
        for ((index, decoded) in changes) {
            val item = checkNotNull(mediaItemAt(index))
            val clipping = decoded?.let { MediaItem.ClippingConfiguration.Builder().setEndPositionUs(it.tail.clipEndUs).build() } ?: MediaItem.ClippingConfiguration.UNSET
            player.replaceMediaItem(index, item.buildUpon().setClippingConfiguration(clipping).build())
        }

        if (decoding == null) {
            val want = wanted.firstOrNull { it.entry.uid !in tails && it.entry.uid !in undecodable } ?: return
            val item = checkNotNull(mediaItemAt(want.index))
            decoding = want.entry.uid
            decoder.decode(item, want.clipEndMs, onDecoded = { tail ->
                decoding = null
                if (tail != null) tails[want.entry.uid] = Decoded(want.entry, tail) else undecodable += want.entry.uid
                update()
            })
        }
    }

    /**
     * Whether the item at [index] can go from clipped to [from]'s tail to clipped to [to]'s (null: unclipped): any but
     * the playing item can, and that one only while both ends are [CLIP_CHANGE_MARGIN_MS] ahead of its position.
     */
    private fun canMoveClip(
        index: Int,
        from: Decoded?,
        to: Decoded?
    ): Boolean {
        if (index != player.currentMediaItemIndex) return true
        val ends = listOfNotNull(from, to).map { it.tail.clipEndUs / 1000 }
        return ends.all { player.currentPosition < it - CLIP_CHANGE_MARGIN_MS }
    }

    /** A tail, and the entry it was decoded for: an entry replaced for a changed song needs its own. */
    private class Decoded(
        val entry: QueueEntry,
        val tail: Tail
    )

    private class Wanted(
        val index: Int,
        val entry: QueueEntry,
        val clipEndMs: Long
    )

    private fun mediaItemAt(index: Int): MediaItem? = if (index == C.INDEX_UNSET || index >= player.mediaItemCount) null else player.getMediaItemAt(index)

    /** Where the entry [uid] is in the queue: at or after the current index, usually, so it looks there first. */
    private fun indexOf(uid: Long): Int? {
        val count = player.mediaItemCount
        val current = player.currentMediaItemIndex.coerceIn(0, count)
        return (current until count).plus(0 until current).firstOrNull { player.getMediaItemAt(it).queueEntryOrNull?.uid == uid }
    }

    /** The index that plays after [index] on its own, as [Player.getNextMediaItemIndex] has it (repeat-one as off). */
    private fun followingIndex(index: Int): Int {
        val timeline = player.currentTimeline
        if (index == C.INDEX_UNSET || index >= timeline.windowCount) return C.INDEX_UNSET
        val repeatMode = if (player.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else player.repeatMode
        return timeline.getNextWindowIndex(index, repeatMode, player.shuffleModeEnabled)
    }

    private fun next(
        current: QueueEntry,
        next: QueueEntry?,
        crossfadeMs: Long
    ): Next = when {
        player.repeatMode == Player.REPEAT_MODE_ONE -> Next.Join
        next == null -> Next.FadeOut
        sameAlbum(current, next) -> Next.Join
        crossfadeClipEndMs(next, crossfadeMs) == null -> Next.Join
        else -> Next.MixInto(next.uid)
    }

    private fun sameAlbum(
        a: QueueEntry,
        b: QueueEntry
    ) = a.song.album != null && a.song.album == b.song.album && a.song.albumArtist == b.song.albumArtist
}
