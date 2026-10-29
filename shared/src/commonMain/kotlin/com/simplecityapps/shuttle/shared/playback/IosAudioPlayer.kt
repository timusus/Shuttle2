package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.mediaprovider.StreamUrlProvider
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.shuttle.model.Song

/**
 * The player the iOS app plays through, implemented in Swift over AudioPlaybackKit's `Player` (`KitAudioPlayer`): a
 * thin adapter, each method one player call. It takes the whole play order and owns the next item, repeat, the gapless
 * join and the audio session, opening each item's stream through [IosItemResolver] itself, as Media3 does;
 * [IosPlayerController] owns the queue, shuffle and skipping failed items, and hands the order over on every edit.
 *
 * Called on the main thread, and delivers every [IosAudioPlayerListener] callback on the main thread, reporting each
 * item under its queue uid.
 */
interface IosAudioPlayer {
    /** Where the player reports what it does; null stops reporting. */
    fun setListener(listener: IosAudioPlayerListener?)

    /** Replaces the order with [order], [currentIndex] current from [startMs]. `Player.setItems`. */
    fun setItems(
        order: List<IosQueueEntry>,
        currentIndex: Int,
        startMs: Long,
        playWhenReady: Boolean
    )

    /** A queue edit that keeps [order]`[currentIndex]` current: it plays on undisturbed. `Player.replaceItems`. */
    fun replaceItems(
        order: List<IosQueueEntry>,
        currentIndex: Int
    )

    /** Moves to [index] of the order last set, at [startMs]. `Player.seek(to:itemAt:)`. */
    fun skipTo(
        index: Int,
        startMs: Long,
        playWhenReady: Boolean
    )

    /** Moves to the item after the current one, wrapping under repeat all (repeat one counts as off). `Player.skipToNext`. */
    fun skipToNext(playWhenReady: Boolean)

    fun setRepeatMode(mode: RepeatMode)

    fun play()

    fun pause()

    /** Seeks within the current item. */
    fun seek(positionMs: Long)

    /** Unloads the current item and stops; the order stays. */
    fun stop()

    /** Playback speed, pitch preserved; 1 is normal. */
    fun setSpeed(speed: Float)

    /**
     * At the end of the current item, pause and make the next item current, paused at its start, none of it heard;
     * reported through [IosAudioPlayerListener.onPausedAtEnd]. Turned off before the end, the join is gapless.
     * `Player.pauseAtEndOfItem`.
     */
    fun setPauseAtEnd(enabled: Boolean)

    /** The position (ms) being heard in the current item, or -1 when nothing is current. */
    fun positionMs(): Long

    /** The current item's duration (ms), or -1 until it's known. */
    fun durationMs(): Long

    /** The equalizer, applied from the next buffer and designed for each rate the audio plays at. */
    fun setEqualizer(design: IosEqualizerDesign)

    /**
     * Where the player reports the rate (Hz) the current item's audio runs through the processors at, on each change
     * (`Player.audioFormat`): null until its audio is heard. Null stops reporting.
     */
    fun setOutputSampleRateListener(listener: ((Int?) -> Unit)?)

    /**
     * ReplayGain's [mode] and [preampDb], applied from the next buffer to the item playing too, as on Android: each item
     * at the tags it resolved with ([IosStream.replayGainTrackDb]), by the rule of `replayGainDb`.
     */
    fun setReplayGain(
        mode: ReplayGainMode,
        preampDb: Float
    )

    /**
     * The [entries] of the order that are movements of a work, replaced whole: in Track mode each plays at its album
     * gain (`replayGainModeAt`). An item takes its choice as it starts, so an edit reaches the items not yet started
     * and never moves the one playing.
     */
    fun setWorkAlbumGain(entries: List<IosQueueEntry>)
}

/** Why an item failed, as the engine's error category maps to what the app does about it. */
enum class IosFailureKind {
    /** The file isn't there or can't be read. */
    Missing,

    /** The bytes are damaged, or in a format this build doesn't play. */
    Undecodable,

    /** The server answered 404 or 410: it no longer has the item. */
    HttpNotFound,

    /** The connection failed or dropped; the copy itself may be fine. */
    Connection,

    Other;

    /** Whether the copy is remembered as missing; a lost connection or an unknown fault only falls back. */
    val marksCopyMissing: Boolean get() = this == Missing || this == Undecodable || this == HttpNotFound
}

/** What the player reports. Swift calls these on the main thread. */
interface IosAudioPlayerListener {
    /** The player's state changed, while [trackId] was its current item. Each report is the state as it stands. */
    fun onStateChanged(
        trackId: String,
        state: IosAudioPlayerState
    )

    /** The player moved on to [trackId] by itself (playing out, or pausing at the end): it's current now. */
    fun onTransition(trackId: String)

    /** [trackId] couldn't be opened or decoded, for the reason [kind] names; the player stops on it. */
    fun onFailed(
        trackId: String,
        kind: IosFailureKind,
        message: String
    )

    /** A position tick while playing, and once a seek lands: [positionMs] into [trackId]. */
    fun onPosition(
        trackId: String,
        positionMs: Long
    )

    /** With pause at the end on, [trackId] played out and the player paused; the transition to the next follows. */
    fun onPausedAtEnd(trackId: String)
}

/** `Player.state`, with ready split by whether it's heard. */
enum class IosAudioPlayerState {
    Idle,
    Loading,
    Playing,
    Paused,

    /** The last item played out with nothing after it. */
    Ended
}

/** One item of the play order handed to the [IosAudioPlayer]: the same pair is the same item. */
data class IosQueueEntry(
    val uid: Long,
    val songId: Long
)

/** Where a song's audio comes from, and its ReplayGain tags (dB, null when untagged). */
data class IosStream(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val replayGainTrackDb: Float? = null,
    val replayGainAlbumDb: Float? = null,
    /**
     * Whether resolving the song again with a start position gives a stream that starts there: a server stream, whose
     * transcode takes `StartTimeTicks`.
     */
    val opensAtPosition: Boolean = false,
    /** A transcode started at the position it was resolved at; a direct-play server stream starts at 0 whatever it's asked. */
    val transcoded: Boolean = false,
    /**
     * The library's bitrate (kbps), file size (bytes) and duration (ms) for a server stream, or -1 if unknown: what its
     * read-ahead on an expensive network is sized by. A stream opened partway through has no size, as its bytes aren't
     * the file's.
     */
    val bitrateKbps: Int = -1,
    val sizeBytes: Long = -1,
    val durationMs: Long = -1
)

/**
 * Resolves a song to the stream the player plays: a `file://` URL for a local file, a provider stream URL for a
 * Jellyfin, Emby or Plex song, with its ReplayGain. Called on the main thread; it may suspend (a server lookup).
 * Throws if the song can't be resolved; the song is then skipped as a failed one, without a playback failure being
 * reported (as Android's `ItemLoader` treats a resolution failure).
 */
fun interface IosStreamResolver {
    /**
     * [startPositionMs] asks a stream that [IosStream.opensAtPosition] to start that far into the song (a transcode
     * started there); 0 is the whole song. Any other stream ignores it. [playRequested] is whether the user is waiting
     * to hear it: only then may a refusal open the paywall, so a queue restored at launch never does. [playId] names
     * this play of the song ([StreamUrlProvider.streamUrl]).
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
