package com.simplecityapps.playback.dsp.crossfade

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.ClippingMediaSource
import androidx.media3.exoplayer.source.MediaSource
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
 * Clips each queue entry's item to end the crossfade's length early (see [crossfadeClipEndMs]). The item itself is
 * unchanged, so the session and queue see the same items; the player's timeline shows the clipped duration.
 */
class CrossfadeClippingMediaSourceFactory(
    private val delegate: MediaSource.Factory,
    private val crossfadeMs: () -> Long
) : MediaSource.Factory {
    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val source = delegate.createMediaSource(mediaItem)
        val clipEndMs = mediaItem.queueEntryOrNull?.let { crossfadeClipEndMs(it, crossfadeMs()) } ?: return source
        return ClippingMediaSource.Builder(source).setEndPositionMs(clipEndMs).build()
    }

    override fun getSupportedTypes(): IntArray = delegate.supportedTypes

    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory = apply { delegate.setDrmSessionManagerProvider(drmSessionManagerProvider) }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory = apply { delegate.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy) }
}

/**
 * Keeps [mixer]'s plans up to date as [player] plays: decodes the tails of the current and next entries (one at a time,
 * the current first) and says what to do with each when its entry ends. It crossfades only into the entry that
 * plays next on its own; not on repeat-one, not between songs of the same album (they may be gapless), and not into a
 * song too short to be clipped. The mixer drops the tail on a skip or seek, so those cut hard.
 */
class Crossfade(
    private val player: ExoPlayer,
    private val mixer: CrossfadeMixer,
    private val decoder: TailDecoder,
    private val crossfadeMs: () -> Long
) : Player.Listener {
    private val tails = mutableMapOf<Long, Tail>()

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
        // The current entry and the next, each with the entry that follows it.
        val currentIndex = player.currentMediaItemIndex
        val nextIndex = followingIndex(currentIndex)
        val entries = listOf(currentIndex to nextIndex, nextIndex to followingIndex(nextIndex))
        val wanted =
            entries.mapNotNull { (index, following) ->
                val item = mediaItemAt(index) ?: return@mapNotNull null
                val entry = item.queueEntryOrNull ?: return@mapNotNull null
                val clipEndMs = crossfadeClipEndMs(entry, crossfadeMs) ?: return@mapNotNull null
                Wanted(item, entry, clipEndMs, mediaItemAt(following)?.queueEntryOrNull)
            }
        // Keep only the tails still wanted, decoded for the current length.
        tails.entries.retainAll { (uid, tail) -> wanted.any { it.entry.uid == uid && tail.clipEndUs == it.clipEndMs * 1000 } }
        if (decoding != null && wanted.none { it.entry.uid == decoding }) {
            decoder.cancel()
            decoding = null
        }

        mixer.plans = wanted.mapNotNull { want -> tails[want.entry.uid]?.let { want.entry.uid to CrossfadePlan(it, next(want.entry, want.following, crossfadeMs)) } }.toMap()

        if (decoding == null) {
            val want = wanted.firstOrNull { it.entry.uid !in tails && it.entry.uid !in undecodable } ?: return
            decoding = want.entry.uid
            decoder.decode(want.item, want.clipEndMs, onDecoded = { tail ->
                decoding = null
                if (tail != null) tails[want.entry.uid] = tail else undecodable += want.entry.uid
                update()
            })
        }
    }

    private class Wanted(
        val item: MediaItem,
        val entry: QueueEntry,
        val clipEndMs: Long,
        /** The entry that plays after this one on its own, if any. */
        val following: QueueEntry?
    )

    private fun mediaItemAt(index: Int): MediaItem? = if (index == C.INDEX_UNSET || index >= player.mediaItemCount) null else player.getMediaItemAt(index)

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
