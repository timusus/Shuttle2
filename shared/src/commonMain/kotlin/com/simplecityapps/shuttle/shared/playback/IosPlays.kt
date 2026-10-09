package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * The plays a stream was resolved for, ended ([IosStreamResolver.endPlay]) once they're none of [livePlayIds], so a
 * server stops its transcode (#722).
 */
internal class IosPlays(
    private val resolver: IosStreamResolver,
    private val scope: CoroutineScope,
    private val livePlayIds: () -> Set<String>
) {
    /** The plays a stream was resolved for that haven't been ended, with their songs. */
    private val resolvedPlays = mutableMapOf<String, Song>()

    private val logger = Logger.tagged("IosPlayerController")

    /**
     * [feed]'s song's stream, or why it has none; a refusal opens the paywall only while the user is waiting to play
     * ([playRequested]). A play [feed] is no longer part of by the time its stream is resolved is ended straight away.
     */
    suspend fun resolve(
        feed: IosFeed,
        startPositionMs: Int,
        playRequested: Boolean
    ): Result<IosStream> = try {
        val song = feed.item.song
        Result.success(resolver.resolve(song, startPositionMs.toLong(), playRequested = playRequested, playId = feed.playId)).also {
            resolvedPlays[feed.playId] = song
            endAbandoned()
        }
    } catch (e: CancellationException) {
        // A resolve cancelled part-way may have opened the play's session before it was: end it unless the play is still live
        resolvedPlays[feed.playId] = feed.item.song
        endAbandoned()
        throw e
    } catch (e: Exception) {
        logger.warn(e) { "Failed to resolve the stream for ${feed.item.song.path}" }
        Result.failure(e)
    }

    /** Ends each resolved play that's no longer live. */
    fun endAbandoned() {
        if (resolvedPlays.isEmpty()) return
        val live = livePlayIds()
        resolvedPlays.filterKeys { it !in live }.keys.forEach(::end)
    }

    /**
     * Ends [playId]'s play. The provider retires the play's session before its first suspension, and it's started
     * undispatched, so a stream resolved for the play afterwards opens a new session the stop can't reach.
     */
    fun end(playId: String) {
        val song = resolvedPlays.remove(playId) ?: return
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Best effort: a provider that fails to end a play must not take playback down with it
            try {
                resolver.endPlay(song, playId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
    }
}

/**
 * The Pro gate refused it, or StoreKit hadn't answered yet: not the song's fault, so it isn't failed for it, and the
 * queue isn't skipped through (every server song would be refused alike). A refusal has opened the paywall.
 */
internal val Result<IosStream>.notAllowed: Boolean
    get() = exceptionOrNull() is ServerStreamNotAllowedException
