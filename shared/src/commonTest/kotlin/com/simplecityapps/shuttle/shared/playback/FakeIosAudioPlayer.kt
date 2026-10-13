package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.queue.RepeatMode

/**
 * A player that behaves as AudioPlaybackKit's `Player` under `KitAudioPlayer` does, as far as the controller can tell:
 * it holds the play order, opens each item it plays through [open] (the controller's `IosItemResolver.resolve`), and
 * fails one whose stream couldn't be resolved or whose url is in [failing]. State reports are the state as it stands
 * when delivered, deduplicated, as `KitAudioPlayer` reports; every report is posted and delivered by [settle].
 * [calls] records what it was asked to do.
 */
class FakeIosAudioPlayer : IosAudioPlayer {
    private enum class Phase { Idle, Loading, Ready, Ended }

    private var listener: IosAudioPlayerListener? = null

    private val events = ArrayDeque<() -> Unit>()

    val calls = mutableListOf<String>()

    /** How the player opens an item's stream, by uid; the test base wires in the controller's item resolver. */
    var open: (Long, (IosStream?, String?) -> Unit) -> Unit = { uid, done -> done(IosStream("song:$uid"), null) }

    /** Urls that fail to open. */
    val failing = mutableSetOf<String>()

    var order = emptyList<IosQueueEntry>()
        private set

    var index: Int? = null
        private set

    var repeatMode = RepeatMode.Off
        private set

    var playWhenReady = false
        private set

    var pausesAtEnd = false
        private set

    /** The stream the current item opened, once it has. */
    var stream: IosStream? = null
        private set

    var position = -1L

    var duration = -1L

    private var phase = Phase.Idle

    private var failed = false

    /** Bumped on every open, so a stream resolved for an item since moved away from is dropped. */
    private var opens = 0

    private var lastReport: Pair<Long, IosAudioPlayerState>? = null

    val currentUid: Long? get() = index?.let { order[it].uid }

    /** An item of the order: its uid as the reports carry it, and the url it opens (or would). */
    data class Track(
        val id: String,
        val url: String
    )

    val current: Track? get() = index?.let { Track("${order[it].uid}", stream?.url ?: "song:${order[it].songId}") }

    /** The item the player moves on to when the current one ends. */
    val next: Track? get() = index?.let { following(it, repeatMode) }?.let { Track("${order[it].uid}", "song:${order[it].songId}") }

    override fun setListener(listener: IosAudioPlayerListener?) {
        this.listener = listener
    }

    override fun setItems(
        order: List<IosQueueEntry>,
        currentIndex: Int,
        startMs: Long,
        playWhenReady: Boolean
    ) {
        calls += "items ${order.map { it.songId }} at $currentIndex@$startMs${if (playWhenReady) " playing" else ""}"
        this.order = order
        index = currentIndex
        lastReport = null
        openCurrent(startMs)
        setPlayWhenReady(playWhenReady)
    }

    override fun replaceItems(
        order: List<IosQueueEntry>,
        currentIndex: Int
    ) {
        calls += "replace ${order.map { it.songId }} at $currentIndex"
        this.order = order
        index = currentIndex
    }

    override fun skipTo(
        index: Int,
        startMs: Long,
        playWhenReady: Boolean
    ) {
        calls += "skip to $index@$startMs${if (playWhenReady) " playing" else ""}"
        moveTo(index, startMs)
        setPlayWhenReady(playWhenReady)
    }

    override fun skipToNext(playWhenReady: Boolean) {
        calls += "skip to next${if (playWhenReady) " playing" else ""}"
        val next = index?.let { following(it, if (repeatMode == RepeatMode.All) RepeatMode.All else RepeatMode.Off) } ?: return
        moveTo(next, 0)
        setPlayWhenReady(playWhenReady)
    }

    /**
     * `seek(to:itemAt:)`: a stopped player only moves its index, and a play opens the item there. A move off a failed
     * item opens its target, as `KitAudioPlayer` hands the order over again when it doesn't play.
     */
    private fun moveTo(
        target: Int,
        startMs: Long
    ) {
        lastReport = null
        if (target == index) {
            seekTo(startMs)
            return
        }
        val stopped = !failed && phase == Phase.Idle
        index = target
        position = startMs
        if (!stopped) openCurrent(startMs)
    }

    override fun setRepeatMode(mode: RepeatMode) {
        if (mode != repeatMode) calls += "repeat $mode"
        repeatMode = mode
    }

    /** A play or pause is always answered with a report, even one that changes nothing, as `KitAudioPlayer` does. */
    override fun play() {
        calls += "play"
        lastReport = null
        setPlayWhenReady(true)
    }

    override fun pause() {
        calls += "pause"
        lastReport = null
        setPlayWhenReady(false)
    }

    /** Whether a play is let through; not, and the player drops the intent, as on a session that won't activate. */
    var acceptsPlay = true

    /** The state last reported. */
    val state: IosAudioPlayerState? get() = lastReport?.second

    /** The id the current item's reports carry. */
    val currentId: String? get() = currentUid?.let { "$it" }

    /** A report the player makes as it stands now, for [trackId], whatever its state. */
    fun emitState(
        state: IosAudioPlayerState,
        trackId: String
    ) = post { listener?.onStateChanged(trackId, state) }

    /** The player pauses by itself, as after a route change it couldn't start again on. */
    fun pauseItself() {
        playWhenReady = false
        report()
        settle()
    }

    private fun setPlayWhenReady(requested: Boolean) {
        // A play while playing changes nothing, so nothing refuses it.
        val value = requested && (acceptsPlay || playWhenReady)
        playWhenReady = value
        if (value && index != null) {
            when {
                failed || phase == Phase.Idle -> openCurrent(position.coerceAtLeast(0))

                phase == Phase.Ended -> {
                    position = 0
                    phase = Phase.Ready
                }
            }
        }
        report()
    }

    override fun seek(positionMs: Long) {
        calls += "seek $positionMs"
        seekTo(positionMs)
    }

    private fun seekTo(positionMs: Long) {
        val uid = currentUid ?: return
        position = positionMs
        if (phase == Phase.Ended) openCurrent(positionMs)
        post { listener?.onPosition("$uid", positionMs) }
    }

    override fun stop() {
        calls += "stop"
        opens++
        phase = Phase.Idle
        failed = false
        stream = null
        lastReport = null
    }

    override fun setSpeed(speed: Float) {
        calls += "speed $speed"
    }

    override fun setPauseAtEnd(enabled: Boolean) {
        calls += "pause at end $enabled"
        pausesAtEnd = enabled
    }

    override fun positionMs(): Long = if (index == null) -1 else position

    override fun durationMs(): Long = duration

    /** Every [setEqualizer], in order; kept apart from [calls], which is the queue's traffic. */
    val equalizers = mutableListOf<IosEqualizerDesign>()

    override fun setEqualizer(design: IosEqualizerDesign) {
        equalizers += design
    }

    private var outputSampleRateListener: ((Int?) -> Unit)? = null

    override fun setOutputSampleRateListener(listener: ((Int?) -> Unit)?) {
        outputSampleRateListener = listener
    }

    /** The current item's audio is heard at [rateHz], or none is (null). */
    fun reportOutputSampleRate(rateHz: Int?) {
        outputSampleRateListener?.invoke(rateHz)
    }

    /** Every [setReplayGain] as mode and preamp, in order; kept apart from [calls] too. */
    val replayGains = mutableListOf<Pair<ReplayGainMode, Float>>()

    override fun setReplayGain(
        mode: ReplayGainMode,
        preampDb: Float
    ) {
        replayGains += mode to preampDb
    }

    /**
     * The current item plays to its end: on into the next item, gaplessly, or, with nothing after it, it ends. Pausing at
     * the end, the player pauses and makes the next item current, paused at its start.
     */
    fun finishTrack() {
        val current = index ?: return
        val uid = order[current].uid
        if (duration > 0) position = duration
        if (pausesAtEnd) {
            playWhenReady = false
            post { listener?.onPausedAtEnd("$uid") }
        }
        val next = following(current, repeatMode)
        if (next == null) {
            phase = Phase.Ended
            report()
        } else if (next == current && !pausesAtEnd) {
            // A repeat joins gaplessly into the item already open: its state doesn't change, so nothing is reported.
            position = 0
            post { listener?.onTransition("$uid") }
        } else if (pausesAtEnd || !openedAhead(next)) {
            // Pausing at the end, nothing was opened ahead: the player opens the next item once it's current.
            index = next
            position = 0
            duration = -1
            lastReport = null
            post { listener?.onTransition("${order[next].uid}") }
            openCurrent(0)
            report()
        }
        settle()
    }

    /**
     * The player opened [next] ahead of the join, as `Player` decodes its armed next: if that answered, the player moves
     * on to it, and one that failed is reported after its transition. Returns false if the open is still waiting.
     */
    private fun openedAhead(next: Int): Boolean {
        val uid = order[next].uid
        var answer: Pair<IosStream?, String?>? = null
        open(uid) { opened, error -> answer = opened to error }
        val (opened, error) = answer ?: return false
        opens++
        index = next
        position = 0
        duration = -1
        lastReport = null
        post { listener?.onTransition("$uid") }
        if (opened == null || opened.url in failing) {
            failed = true
            phase = Phase.Idle
            stream = null
            post { listener?.onFailed("$uid", error ?: "Can't open ${opened?.url}") }
        } else {
            stream = opened
            phase = Phase.Ready
            report()
        }
        return true
    }

    /** The current item fails while it plays, as a stream whose connection dropped. */
    fun failCurrent() {
        val uid = currentUid ?: return
        failed = true
        phase = Phase.Idle
        post { listener?.onFailed("$uid", "Connection lost") }
        settle()
    }

    /** A position tick while playing. */
    fun tick(positionMs: Long) {
        position = positionMs
        val uid = currentUid ?: return
        post { listener?.onPosition("$uid", positionMs) }
        settle()
    }

    /** Delivers every report made, and any made meanwhile. */
    fun settle() {
        while (events.isNotEmpty()) events.removeFirst().invoke()
    }

    /** Clears [calls], so a test sees only what's asked of the player after this. */
    fun clearCalls() = calls.clear()

    private fun following(
        current: Int,
        mode: RepeatMode
    ): Int? = when (mode) {
        RepeatMode.One -> current
        RepeatMode.All -> (current + 1) % order.size
        RepeatMode.Off -> (current + 1).takeIf { it < order.size }
    }

    private fun openCurrent(startMs: Long) {
        val current = index ?: return
        val uid = order[current].uid
        val serial = ++opens
        phase = Phase.Loading
        failed = false
        stream = null
        position = startMs
        report()
        // The player asks from its decode thread, after the call that opened the item has returned.
        post { open(uid, opened(serial, uid)) }
    }

    private fun opened(
        serial: Int,
        uid: Long
    ): (IosStream?, String?) -> Unit = { opened, error ->
        if (serial == opens) {
            if (opened == null || opened.url in failing) {
                failed = true
                phase = Phase.Idle
                post { listener?.onFailed("$uid", error ?: "Can't open ${opened?.url}") }
            } else {
                stream = opened
                phase = Phase.Ready
                report()
            }
        }
    }

    /** Posts a report of the state as it stands when delivered, as `KitAudioPlayer` reads it, unless it repeats the last. */
    private fun report() {
        post {
            val uid = currentUid ?: return@post
            if (failed) return@post
            val state = when (phase) {
                Phase.Idle -> IosAudioPlayerState.Idle
                Phase.Loading -> IosAudioPlayerState.Loading
                Phase.Ready -> if (playWhenReady) IosAudioPlayerState.Playing else IosAudioPlayerState.Paused
                Phase.Ended -> IosAudioPlayerState.Ended
            }
            if (lastReport == uid to state) return@post
            lastReport = uid to state
            listener?.onStateChanged("$uid", state)
        }
    }

    private fun post(event: () -> Unit) {
        events.addLast(event)
    }
}
