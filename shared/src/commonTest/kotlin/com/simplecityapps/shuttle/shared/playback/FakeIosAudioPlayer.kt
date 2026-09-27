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
        this.playWhenReady = playWhenReady
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
            setState(if (playWhenReady) IosAudioPlayerState.Playing else IosAudioPlayerState.Paused)
        }
    }

    override fun setNext(next: IosAudioTrack?) {
        calls += "next ${next?.url}"
        this.next = next
    }

    override fun play() {
        calls += "play"
        playWhenReady = true
        if (current != null && state == IosAudioPlayerState.Paused) setState(IosAudioPlayerState.Playing)
    }

    override fun pause() {
        calls += "pause"
        playWhenReady = false
        if (state == IosAudioPlayerState.Playing) setState(IosAudioPlayerState.Paused)
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
        post { listener?.onStateChanged(id, state) }
    }

    private fun post(event: () -> Unit) {
        events.addLast(event)
    }
}
