package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.shuttle.model.Song

/**
 * The audio engine the iOS app plays through, implemented in Swift over `MusicPlaybackController` (ios/Playback): a
 * thin adapter, each method one controller call. The engine only ever knows the current track and the next one;
 * [IosPlayerController] owns the queue, shuffle, repeat and skipping failed tracks, and feeds it those two.
 *
 * Called on the main thread. The Swift adapter hops each call to the main actor as Podcasts' bridge does, and
 * delivers every [IosAudioPlayerListener] callback on the main thread, in the order the engine made them.
 */
interface IosAudioPlayer {
    /** Where the engine reports what it does; null stops reporting. */
    fun setListener(listener: IosAudioPlayerListener?)

    /** Replaces whatever was loaded with [current], starting at [startMs], and [next] after it. `MusicPlaybackController.load`. */
    fun load(
        current: IosAudioTrack,
        next: IosAudioTrack?,
        startMs: Long,
        playWhenReady: Boolean
    )

    /** Sets, replaces or (null) clears the track after the current one, for a gapless handover. `setNext`. */
    fun setNext(next: IosAudioTrack?)

    fun play()

    fun pause()

    /**
     * Seeks within the current track. `seek(toMs:)`. A track the engine can't seek (a stream of unknown length, like a
     * progressive transcode) plays on untouched and is reported through [IosAudioPlayerListener.onSeekUnsupported].
     */
    fun seek(positionMs: Long)

    /** Drops the current and next tracks and stops. */
    fun stop()

    /** Playback speed, pitch preserved; 1 is normal. */
    fun setSpeed(speed: Float)

    /** The position (ms) being heard in the current track, or -1 when nothing is loaded. `position`. */
    fun positionMs(): Long

    /** The current track's duration (ms), or -1 until it's known. `durationMs`. */
    fun durationMs(): Long
}

/** What the engine reports. Swift calls these on the main thread. */
interface IosAudioPlayerListener {
    /**
     * The engine's state changed, while [trackId] was its current track. The id tells a state change for the track
     * just loaded from one the engine made for the track before, which can arrive after the new load was asked for.
     */
    fun onStateChanged(
        trackId: String,
        state: IosAudioPlayerState
    )

    /** The next track became current: its first frame is being heard. [trackId] is its id. */
    fun onTransition(trackId: String)

    /** [trackId] couldn't be opened or decoded. The engine carries on as if it had ended. */
    fun onFailed(
        trackId: String,
        message: String
    )

    /** A position tick while playing, and once on pausing: [positionMs] into [trackId]. */
    fun onPosition(
        trackId: String,
        positionMs: Long
    )

    /**
     * [trackId] can't be sought to [positionMs] (ms into the track as the engine has it): its stream has no length, so
     * no byte maps to a time. A seek leaves it playing where it was; a load that asked to start there starts it at its
     * beginning. [IosPlayerController] re-opens the stream at the position instead.
     */
    fun onSeekUnsupported(
        trackId: String,
        positionMs: Long
    )
}

/** `MusicPlaybackController.State`. */
enum class IosAudioPlayerState {
    Idle,
    Loading,
    Playing,
    Paused,

    /** The last track played out with nothing after it. */
    Ended
}

/**
 * A track as the engine sees it: `PlaybackTrack(uid:url:headers:gainDb:)`. [id] is unique to this handing of the track
 * to the engine (a queue item played twice in a row, on repeat one, gets two), so a report for a track the engine has
 * since dropped can be told apart.
 */
class IosAudioTrack(
    val id: String,
    val url: String,
    val headers: Map<String, String>,
    /** ReplayGain in dB, resolved (track or album mode, preamp, clipping) by [IosStreamResolver]; 0 is unity. */
    val gainDb: Float
) {
    override fun toString(): String = "IosAudioTrack($id, $url)"
}

/** Where a song's audio comes from, and its gain. */
data class IosStream(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val gainDb: Float = 0f,
    /**
     * Whether resolving the song again with a start position gives a stream that starts there: a server stream, whose
     * transcode takes `StartTimeTicks`. That is how a seek the engine can't make (a progressive transcode) is made.
     */
    val opensAtPosition: Boolean = false
)

/**
 * Resolves a song to the stream the engine plays: a `file://` URL for a local file, a provider stream URL for a
 * Jellyfin, Emby or Plex song, with its ReplayGain. Called on the main thread; it may suspend (a server lookup).
 * Throws if the song can't be resolved; the song is then skipped as a failed one, without a playback failure being
 * reported (as Android's `ItemLoader` treats a resolution failure).
 */
fun interface IosStreamResolver {
    /**
     * [startPositionMs] asks a stream that [IosStream.opensAtPosition] to start that far into the song (a transcode
     * started there); 0 is the whole song. Any other stream ignores it.
     */
    suspend fun resolve(
        song: Song,
        startPositionMs: Long
    ): IosStream
}
