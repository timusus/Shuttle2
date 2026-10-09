package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.mediaprovider.StreamUrlProvider
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

    /**
     * Pause on the current track's last frame rather than carry on into the next, which stays next and none of which
     * is heard; reported through [IosAudioPlayerListener.onPausedAtEnd]. Turned off before the end, the join is
     * gapless. Kept across loads. `setPauseAtEnd(_:)`.
     */
    fun setPauseAtEnd(enabled: Boolean)

    /** The position (ms) being heard in the current track, or -1 when nothing is loaded. `position`. */
    fun positionMs(): Long

    /** The current track's duration (ms), or -1 until it's known. `durationMs`. */
    fun durationMs(): Long

    /**
     * The equalizer, applied from the next chunk the engine renders: off, or [preampDb] (dB, the headroom attenuation
     * and the user's preamp together) and one peaking biquad per band, five [coefficients] each (b0, b1, b2, a1, a2,
     * normalised to a0 = 1) designed at [engineSampleRate]. `setEqualizer(_:)`.
     */
    fun setEqualizer(
        enabled: Boolean,
        preampDb: Float,
        coefficients: DoubleArray
    )

    /**
     * The rate (Hz) the engine renders and filters at, whatever the output route's rate: every track is resampled to
     * it, so it's fixed for the engine's life. `outputSampleRate`.
     */
    fun engineSampleRate(): Int
}

/** What the engine reports. Swift calls these on the main thread. */
interface IosAudioPlayerListener {
    /**
     * The engine's state changed, while [trackId] was its current track. The id tells a state change for the track
     * just loaded from one the engine made for the track before, which can arrive after the new load was asked for.
     * [superseded]: the engine made it before it took a load, play, pause or stop asked of it since, so it isn't the
     * answer to that: a paused one isn't a later play's refusal (#708), and a playing one isn't playing after a pause.
     */
    fun onStateChanged(
        trackId: String,
        state: IosAudioPlayerState,
        superseded: Boolean
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

    /** With pause at the end on, [trackId]'s last frame was heard and the engine paused there; a paused state follows. */
    fun onPausedAtEnd(trackId: String)
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
 * A track as the engine sees it: `PlaybackTrack(uid:url:headers:gainDb:expectedDurationMs:bitrateKbps:sizeBytes:)`. [id] is unique to this
 * handing of the track to the engine (a queue item played twice in a row, on repeat one, gets two), so a report for a
 * track the engine has since dropped can be told apart. The engine tells two hand-overs of the same stream by [url] and
 * [headers] instead: a next it pre-opened is kept when a load hands the same stream back, as the next or the current
 * track. A server URL names its play session, so only the stream handed over before matches.
 */
class IosAudioTrack(
    val id: String,
    val url: String,
    val headers: Map<String, String>,
    /** ReplayGain in dB, resolved (track or album mode, preamp, clipping) by [IosStreamResolver]; 0 is unity. */
    val gainDb: Float,
    /**
     * How long the stream should run (ms), from the library, or -1 if unknown. The engine times opening the next track
     * by the current one's end, and a progressive transcode's container doesn't say where that is.
     */
    val expectedDurationMs: Long,
    /**
     * The library's bitrate (kbps) and file size (bytes), or -1 if unknown: what the engine sizes a stream's read-ahead
     * on mobile data by. A stream opened partway through has no size, as its bytes aren't the file's.
     */
    val bitrateKbps: Int = -1,
    val sizeBytes: Long = -1
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
     * started there); 0 is the whole song. Any other stream ignores it. [playRequested] is whether the user is waiting
     * to hear it: only then may a refusal open the paywall, so a queue restored at launch never does. [playId] names
     * this play of the song, the same when a seek resolves it again ([StreamUrlProvider.streamUrl]).
     */
    suspend fun resolve(
        song: Song,
        startPositionMs: Long,
        playRequested: Boolean,
        playId: String
    ): IosStream

    /** [playId], a play of [song], is over: the server can drop what it holds for it ([StreamUrlProvider.endPlay]). */
    suspend fun endPlay(
        song: Song,
        playId: String
    ) = Unit
}
