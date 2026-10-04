package com.simplecityapps.shuttle.shared.playback

/**
 * An engine that behaves as `MusicPlaybackController` does, as far as the controller can tell: it reports each state
 * change, and fails a track whose url is in [failing]. Reports are posted, as the Swift engine delivers them
 * asynchronously, and delivered by [settle]. [calls] records what it was asked to do.
 */
class FakeIosAudioPlayer : IosAudioPlayer {
    private var listener: IosAudioPlayerListener? = null

    private val events = ArrayDeque<() -> Unit>()

    val calls = mutableListOf<String>()

    /** Urls that fail to open. */
    val failing = mutableSetOf<String>()

    /** Urls (before any `?`) of streams with no length, which can't be sought: a progressive transcode. */
    val unseekable = mutableSetOf<String>()

    private fun IosAudioTrack.isUnseekable() = url.substringBefore('?') in unseekable

    var current: IosAudioTrack? = null
        private set

    var next: IosAudioTrack? = null
        private set

    var state = IosAudioPlayerState.Idle
        private set

    var position = -1L

    var duration = -1L

    private var playWhenReady = false

    /** Loads, plays, pauses and stops taken, as the engine stamps them on its state reports. */
    private var commands = 0

    /** [commands] when the last state report was made: a command with no report of its own is answered. */
    private var commandsReported = 0

    /**
     * The last report delivered was playing, for the current track, and not superseded; nothing asked to pause, load or
     * stop since. As `EngineAudioPlayer` does, a play then isn't sent: the engine's playing report is what it knows, and
     * one still in flight doesn't count.
     */
    private var reportedPlaying = false

    /**
     * When false, [play] (unless already playing, which changes nothing) and a load that asked to play are refused, as
     * an engine whose output won't start (the session wouldn't activate) does: the track is still prepared, ending
     * paused, and the refusal is that paused report at the command's count, delivered by [settle].
     */
    var acceptsPlay = true

    override fun setListener(listener: IosAudioPlayerListener?) {
        this.listener = listener
    }

    override fun load(
        current: IosAudioTrack,
        next: IosAudioTrack?,
        startMs: Long,
        playWhenReady: Boolean
    ) {
        calls += "load ${current.url}@$startMs${if (playWhenReady) " playing" else ""}${next?.let { " next ${it.url}" } ?: ""}"
        this.current = current
        this.next = next
        val starts = playWhenReady && acceptsPlay
        this.playWhenReady = starts
        commands++
        reportedPlaying = false
        position = startMs
        setState(IosAudioPlayerState.Loading)
        if (startMs > 0 && current.isUnseekable()) {
            // The engine starts it at its beginning instead, and says so before its state.
            position = 0
            post { listener?.onSeekUnsupported(current.id, startMs) }
        }
        if (current.url in failing) {
            val id = current.id
            post { listener?.onFailed(id, "Can't open ${current.url}") }
            // A failed current track counts as ended, and the engine carries on into its next.
            if (next != null) transition() else setState(IosAudioPlayerState.Ended)
        } else {
            setState(if (starts) IosAudioPlayerState.Playing else IosAudioPlayerState.Paused)
        }
    }

    override fun setNext(next: IosAudioTrack?) {
        calls += "next ${next?.url}"
        this.next = next
    }

    override fun play() {
        calls += "play"
        if (reportedPlaying) return
        commands++
        if (acceptsPlay) {
            playWhenReady = true
            if (current != null && state == IosAudioPlayerState.Paused) setState(IosAudioPlayerState.Playing)
        }
        answer()
    }

    override fun pause() {
        calls += "pause"
        commands++
        reportedPlaying = false
        playWhenReady = false
        if (state == IosAudioPlayerState.Playing) setState(IosAudioPlayerState.Paused)
        answer()
    }

    override fun seek(positionMs: Long) {
        calls += "seek $positionMs"
        val track = current ?: return
        if (track.isUnseekable()) {
            post { listener?.onSeekUnsupported(track.id, positionMs) }
        } else {
            position = positionMs
        }
    }

    override fun stop() {
        calls += "stop"
        commands++
        reportedPlaying = false
        current = null
        next = null
        state = IosAudioPlayerState.Idle
        position = -1
    }

    override fun setSpeed(speed: Float) {
        calls += "speed $speed"
    }

    override fun positionMs(): Long = if (current == null) -1 else position

    override fun durationMs(): Long = duration

    /** What [setEqualizer] was last given. */
    class Equalizer(
        val enabled: Boolean,
        val preampDb: Float,
        val coefficients: DoubleArray
    )

    /** Every [setEqualizer], in order; kept apart from [calls], which is the queue's traffic. */
    val equalizers = mutableListOf<Equalizer>()

    var sampleRate = 48_000

    override fun setEqualizer(
        enabled: Boolean,
        preampDb: Float,
        coefficients: DoubleArray
    ) {
        equalizers += Equalizer(enabled, preampDb, coefficients)
    }

    override fun engineSampleRate(): Int = sampleRate

    /** The current track plays to its end: on into the next track, or it ends. */
    fun finishTrack() {
        val next = next
        when {
            next == null -> setState(IosAudioPlayerState.Ended)

            next.url in failing -> {
                post { listener?.onFailed(next.id, "Can't open ${next.url}") }
                this.next = null
                setState(IosAudioPlayerState.Ended)
            }

            else -> transition()
        }
        settle()
    }

    /**
     * The engine pauses on its own, as `MusicPlaybackController` does when it can't start again after a route change:
     * loading while it retries, then paused, with its own intent dropped.
     */
    fun pauseItself() {
        playWhenReady = false
        setState(IosAudioPlayerState.Loading)
        setState(IosAudioPlayerState.Paused)
        settle()
    }

    /** A position tick while playing. */
    fun tick(positionMs: Long) {
        position = positionMs
        val id = current?.id ?: return
        post { listener?.onPosition(id, positionMs) }
        settle()
    }

    /** Delivers every report made, and any made meanwhile. */
    fun settle() {
        while (events.isNotEmpty()) events.removeFirst().invoke()
    }

    /** Clears [calls], so a test sees only what's asked of the engine after this. */
    fun clearCalls() = calls.clear()

    /** Queues a state report for [trackId], delivered by [settle]. An id the controller has replaced is ignored by it. */
    fun emitState(
        state: IosAudioPlayerState,
        trackId: String
    ) = report(trackId, state)

    private fun transition() {
        val arrived = checkNotNull(next)
        current = arrived
        next = null
        position = 0
        post { listener?.onTransition(arrived.id) }
        if (arrived.url in failing) {
            post { listener?.onFailed(arrived.id, "Can't open ${arrived.url}") }
            setState(IosAudioPlayerState.Ended)
        } else {
            setState(if (playWhenReady) IosAudioPlayerState.Playing else IosAudioPlayerState.Paused)
        }
    }

    private fun setState(state: IosAudioPlayerState) {
        this.state = state
        val id = current?.id ?: return
        report(id, state)
    }

    /** A command that changed no state is answered with the state it left, but for an end, which is said once. */
    private fun answer() {
        if (commandsReported == commands || state == IosAudioPlayerState.Ended) return
        current?.id?.let { report(it, state) }
    }

    /** Posts [state] for [trackId], stamped with the commands taken so far, as the engine does. */
    private fun report(
        trackId: String,
        state: IosAudioPlayerState
    ) {
        val stamp = commands
        commandsReported = stamp
        post {
            val superseded = stamp < commands
            reportedPlaying = state == IosAudioPlayerState.Playing && trackId == current?.id && !superseded
            listener?.onStateChanged(trackId, state, superseded)
        }
    }

    private fun post(event: () -> Unit) {
        events.addLast(event)
    }
}
