import AVFoundation
import os

/// The engine's category, for the underruns: one
/// `log stream --predicate 'category == "audio-engine"'` shows the node running dry beside the
/// fetch that let it (#896).
let engineLog = Logger(subsystem: "com.simplecityapps.shuttle2", category: "audio-engine")
/// The app's cold-start signposts' log (`StartupTrace`), so Instruments shows a play's start on the same track.
let startSignposter = OSSignposter(subsystem: "com.simplecityapps.shuttle", category: "Startup")

/// **Gapless two-item player: the current track and the next, on one AVAudioPlayerNode.**
///
/// Graph: `AVAudioPlayerNode → AVAudioUnitTimePitch → mainMixer → output`, all at one fixed
/// format (stereo float32 at ``outputSampleRate``). Every track is decoded and converted into that
/// format by its ``TrackPCMSource``, run through the ``PCMProcessor`` chain (ReplayGain, EQ,
/// limiter), and scheduled on the node as consecutive buffers. When the current track's source
/// ends the same buffer carries on with the next track's first frames, so the join is sample-exact
/// whatever the two tracks' rates were: the node never sees a boundary, only one stream.
///
/// **Timeline.** Scheduled frames are numbered from the last load or seek (the stream index). A
/// segment list maps stream indices to `(track, media frame)`; the player node's sample time maps
/// to the stream index through anchors, which only move when the node starved (an underrun on a
/// slow network plays silence the stream did not contain). Position, the transition to the next
/// track and the end of the queue all come from that mapping, so they follow what was heard.
///
/// **Pre-opening.** The next track is opened, and its first chunk decoded, on a background queue
/// ahead of the join, so a slow HTTP open or a transcode that takes seconds to start is ready by
/// then. How far ahead is ``PreopenLead``: `preopenSeconds`, or twice the slowest recent open when
/// that is longer, so a server that takes longer than the window to answer still makes the join.
/// The end is the container's duration, or ``PlaybackTrack/expectedDurationMs``; with neither,
/// the next is opened once `steadySeconds` of the current track have been read since its load or
/// last seek, after the current track's own start and past a quick skip. Reaching the join before
/// the open is done, the stream waits for it: the node runs dry and resumes on the next track's
/// first frame. A pre-opened track that stops being next (replaced, cleared, a load of another
/// stream) is cancelled; one that hasn't been read yet survives a seek, and a load that hands its
/// stream back (``PlaybackTrack/streamIdentity``), as the next again or as the current track (a
/// skip onto it), which then starts on what was already opened.
///
/// **Threading.** Public methods may be called from any thread and return at once. Everything
/// that touches a source (seek, read: blocking I/O over HTTP) and every node operation runs on one
/// serial engine queue; a next track's open runs on a background queue, and nothing touches its
/// source until the open is done. A seek first interrupts a read stalled on the network so it is
/// not queued behind it. Callbacks arrive on `callbackQueue`.
///
/// The engine-queue work is split by responsibility across `MusicPlaybackController+*.swift`.
public final class MusicPlaybackController {

    public enum State: String {
        case idle, loading, playing, paused, ended
    }

    /// How the engine renders. `.offline` is manual rendering, for tests: nothing plays, the test
    /// pulls frames with ``renderOffline(frameCount:)`` and drives the scheduler with
    /// ``pumpForTesting()``.
    public enum RenderingMode {
        case realtime
        case offline(maximumFrameCount: AVAudioFrameCount)
    }

    /// The rate every track is converted to. The EQ coefficients must be computed for it.
    public let outputSampleRate: Double
    public let outputChannelCount = 2

    // MARK: Callbacks (set before use; read under `callbackLock`)

    /// The playing state changed, with the uid of the current track then (nil when there is none), so
    /// a listener can tell a late report for a track it has since replaced, and the number of commands
    /// (``load(current:next:startMs:playWhenReady:)``, ``play()``, ``pause()``, ``stop()``) taken before it.
    /// Commands are taken in the order they're made, so the count tells a report made before a command
    /// from its answer: a paused report from before a play doesn't refuse it. Every command is answered with a
    /// report at its count, even one that changes nothing (a play while playing, a pause while paused), so the
    /// last report always says where the engine is after the last command; but for the end of a track, which
    /// is reported once.
    public var onStateChanged: ((State, String?, Int) -> Void)? {
        get { callbackLock.withLock { callbacks.state } }
        set { callbackLock.withLock { callbacks.state = newValue } }
    }

    /// The next track became the current one: its first frame is now playing (to within the
    /// position tick). The argument is the new current track's uid.
    public var onTransition: ((String) -> Void)? {
        get { callbackLock.withLock { callbacks.transition } }
        set { callbackLock.withLock { callbacks.transition = newValue } }
    }

    /// A track could not be opened or decoded. Playback moves on as if it had ended.
    public var onFailed: ((String, Error) -> Void)? {
        get { callbackLock.withLock { callbacks.failed } }
        set { callbackLock.withLock { callbacks.failed = newValue } }
    }

    /// Every position tick while playing: current uid and position in ms.
    public var onPosition: ((String, Int64) -> Void)? {
        get { callbackLock.withLock { callbacks.position } }
        set { callbackLock.withLock { callbacks.position = newValue } }
    }

    /// The current track can't be sought to the position (uid, ms into the track): its source
    /// isn't seekable (``TrackPCMSource/isSeekable``, a progressive transcode). A seek leaves it
    /// playing where it was; a load at a position starts it at its beginning. The owner re-opens
    /// the stream at the position.
    public var onSeekUnsupported: ((String, Int64) -> Void)? {
        get { callbackLock.withLock { callbacks.seekUnsupported } }
        set { callbackLock.withLock { callbacks.seekUnsupported = newValue } }
    }

    /// The current track's last frame was heard with ``setPauseAtEnd(_:)`` on, and the engine paused there
    /// (reported paused after this). The argument is its uid.
    public var onPausedAtEnd: ((String) -> Void)? {
        get { callbackLock.withLock { callbacks.pausedAtEnd } }
        set { callbackLock.withLock { callbacks.pausedAtEnd = newValue } }
    }

    /// Readies the output for a play: the owner activates its audio session. False refuses the play, which
    /// then stays paused as a play the engine can't start does. Called on a queue of its own as a play or a
    /// load that plays is made, so it runs while the load opens and seeks its track; the engine waits for it
    /// only to start its output (#687).
    public var activateOutput: (() -> Bool)? {
        get { callbackLock.withLock { callbacks.activateOutput } }
        set { callbackLock.withLock { callbacks.activateOutput = newValue } }
    }

    // MARK: Engine

    let engine = AVAudioEngine()
    /// Starts the stopped engine. Tests replace it to fail a start.
    var startEngine: (AVAudioEngine) throws -> Void = { try $0.start() }
    /// What an underrun's hold is timed by. Tests replace it to reach ``UnderrunResumeRule``'s cap without waiting.
    var clock: () -> TimeInterval = StartupTiming.now
    /// How long a start that failed after a route change waits before it's tried again, and how many
    /// times it is (#715).
    var startRetryDelay: DispatchTimeInterval = .milliseconds(500)
    static let startRetryAttempts = 4
    let player = AVAudioPlayerNode()
    let timePitch = AVAudioUnitTimePitch()
    let format: AVAudioFormat
    let renderingMode: RenderingMode
    let engineQueue = DispatchQueue(label: "com.simplecityapps.shuttle2.playback.engine", qos: .userInitiated)
    /// Set on `engineQueue`, so a source's wait hook can tell it's called inside the engine's own read.
    static let engineQueueKey = DispatchSpecificKey<Bool>()
    /// Where next tracks are opened. Concurrent: a cancelled open may still be unwinding.
    let prepareQueue = DispatchQueue(
        label: "com.simplecityapps.shuttle2.playback.prepare", qos: .userInitiated, attributes: .concurrent
    )
    /// Every open in flight on `prepareQueue`, for the tests to wait on.
    let opening = DispatchGroup()
    /// Where ``activateOutput`` runs. Serial: activations finish in the order the plays were made.
    private let activationQueue = DispatchQueue(label: "com.simplecityapps.shuttle2.playback.activation", qos: .userInitiated)
    let callbackQueue: DispatchQueue

    /// How far ahead of the playhead audio is decoded and scheduled. Also how late an EQ change is
    /// heard, so it is short; the byte source's own read-ahead is what rides out the network.
    let scheduleAheadFrames: Int64
    /// How long before the current track's end the next is opened. See the class doc.
    var preopenLead: PreopenLead
    /// How much of a current track of unknown length is read before the next is opened.
    let steadyFrames: Int64
    static let chunkFrames = 4096
    /// How much is scheduled before a play starts the node; the rest of `scheduleAheadFrames` is decoded
    /// while the first of it plays (#687).
    static let startFrames = Int64(chunkFrames * 2)

    // MARK: Engine-queue state

    var current: Slot?
    var next: Slot?
    /// The slot frames are being read from: `current`, then `next` once current's source ended.
    var reading: Slot?
    var playWhenReady = false
    /// The activation the last play (or load that plays) asked for, until the engine starts on it or a pause,
    /// paused load or stop drops it.
    var pendingActivation: OutputActivation?
    var state: State = .idle
    /// Commands taken so far, stamped on each state report (``onStateChanged``).
    var commandsTaken = 0
    /// `commandsTaken` when the last state report was made: a command with no report of its own is answered.
    var commandsReported = 0
    let processor: PCMProcessor
    var pendingEqualizer: EqualizerSettings?
    var pendingLimiter: LimiterSettings?
    /// Frames fed to the processor since the last restart. Output frame n is input frame n
    /// (``PCMProcessor`` hides the limiter's delay), so this is also where the next segment starts.
    var inputIndex: Int64 = 0
    /// Frames scheduled on the node since the last restart.
    var outputIndex: Int64 = 0
    var buffersInFlight = 0
    var starved = false
    /// The node ran dry while playing — an underrun, not the queue's end: when (``StartupTiming/now()``)
    /// and where in the current track. Logged as it starts and as it ends (#896). The state stays
    /// playing, but the owner hears loading for as long as it lasts, and playing again once a buffer
    /// reaches the node, so the listener sees the buffering (#897). A restart (a seek, a rebuild) keeps
    /// it: the listener hears silence until the restarted stream's first buffer.
    var underrun: (since: TimeInterval, ms: Int64)?
    /// Decoded while an underrun lasts on a playing node, kept off it until ``UnderrunResumeRule`` says go on; the
    /// underrun's end schedules it. A restart drops it with the old position's frames.
    var heldForResume: [AVAudioPCMBuffer] = []
    /// When the first of `heldForResume` was held: the cap runs from there, not from the silence's start, so a long
    /// stall doesn't let the first small buffer straight through.
    var heldSince: TimeInterval = 0
    /// The last buffer health line: when, and whether it was under `lowBufferSeconds`.
    var lastHealthLog: (at: TimeInterval, low: Bool)?
    static let healthLogSeconds: TimeInterval = 10
    static let lowBufferSeconds: Double = 5
    /// The queue's last frame is scheduled (current ended with no next, or `pausingAtEnd`).
    var drained = false
    /// ``setPauseAtEnd(_:)``: the stream stops at the current track's end instead of carrying on into the next.
    var pauseAtEnd = false
    /// The drained end is the current track's, held back from the next by `pauseAtEnd`: heard, it pauses
    /// rather than ends.
    var pausingAtEnd = false
    /// Paused on the current track's last frame by `pausingAtEnd`: a play carries on into the next.
    var pausedAtEnd = false
    /// Bumped by every restart; completions from buffers a restart discarded are ignored.
    var generation = 0
    /// Frames an interrupted read got before its interrupt, kept off the node until the seek behind it says
    /// whether they're still the stream's (``resumeReadingNext()``) or the old position's (a restart).
    var heldChunk: AVAudioPCMBuffer?
    var scratch: [Float]
    var ticker: DispatchSourceTimer?
    var timePitchInGraph = false
    /// The start being timed: from a load or play to the node's first rendered frame (#687).
    var startTiming: StartupTiming?
    /// Tells a render watch for an earlier start to stop.
    var startTimingSerial = 0
    /// When the current load was ready, paused: a play close behind it is still the load's start.
    var readyPausedAt: TimeInterval?
    /// The last start's record, once its line was logged.
    var lastStartTiming: StartupTiming?
    /// The signposted interval of `startTiming`.
    var startSignpost: OSSignpostIntervalState?
    /// A play request no start has answered yet (``notePlayRequest(_:)``).
    var pendingPlayRequest: StartupTiming.PlayRequest?

    // MARK: Shared state (any thread, under `timelineLock`)

    let timelineLock = NSLock()
    var timeline = Timeline()
    let activeSourceLock = NSLock()
    var activeSource: TrackPCMSource?
    /// Configuration changes posted so far (any thread). A route change can post several in a row;
    /// only the last one queued rebuilds (#813).
    let configurationChangeLock = NSLock()
    var configurationChanges = 0

    struct Callbacks {
        var state: ((State, String?, Int) -> Void)?
        var transition: ((String) -> Void)?
        var failed: ((String, Error) -> Void)?
        var position: ((String, Int64) -> Void)?
        var seekUnsupported: ((String, Int64) -> Void)?
        var pausedAtEnd: ((String) -> Void)?
        var activateOutput: (() -> Bool)?
    }

    let callbackLock = NSLock()
    var callbacks = Callbacks()

    public init(
        outputSampleRate: Double = 48_000,
        renderingMode: RenderingMode = .realtime,
        scheduleAheadSeconds: Double = 1.0,
        preopenSeconds: Double = 10,
        steadySeconds: Double = 5,
        callbackQueue: DispatchQueue = .main
    ) throws {
        self.outputSampleRate = outputSampleRate
        self.renderingMode = renderingMode
        self.callbackQueue = callbackQueue
        self.scheduleAheadFrames = Int64(scheduleAheadSeconds * outputSampleRate)
        self.preopenLead = PreopenLead(minimumSeconds: preopenSeconds)
        self.steadyFrames = Int64(steadySeconds * outputSampleRate)
        guard let format = AVAudioFormat(standardFormatWithSampleRate: outputSampleRate, channels: 2) else {
            throw TrackSourceError.failed("no stereo float format at \(outputSampleRate) Hz")
        }
        self.format = format
        processor = PCMProcessor(sampleRate: outputSampleRate, channelCount: 2)
        scratch = [Float](repeating: 0, count: Self.chunkFrames * 2)

        engineQueue.setSpecific(key: Self.engineQueueKey, value: true)
        engine.attach(player)
        engine.attach(timePitch)
        connectGraph(speed: 1)

        if case let .offline(maximumFrameCount) = renderingMode {
            try engine.enableManualRenderingMode(.offline, format: format, maximumFrameCount: maximumFrameCount)
            try engine.start()
        } else {
            // Allocates the output's resources now, so the first play only has to start it (#687).
            engine.prepare()
        }
        // An offline engine never posts it; tests do, to stand in for a route change.
        NotificationCenter.default.addObserver(
            self, selector: #selector(engineConfigurationChanged),
            name: .AVAudioEngineConfigurationChange, object: engine
        )
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
        ticker?.cancel()
        engine.stop()
    }

    // MARK: - Public API

    /// Replace the queue with `current` (starting at `startMs`) and `next`. The next track already
    /// loaded, opened or opening, is kept if none of it has been read and `current` or `next` is
    /// its stream (``PlaybackTrack/streamIdentity``): a skip onto it starts on what was already
    /// opened, and a stream re-opened for a seek hands its next back.
    public func load(current track: PlaybackTrack, next nextTrack: PlaybackTrack?, startMs: Int64 = 0, playWhenReady: Bool) {
        let requestedAt = StartupTiming.now()
        interruptActiveRead()
        let activation = playWhenReady ? beginActivation() : nil
        engineQueue.async { [self] in
            commandsTaken += 1
            pendingActivation = activation
            defer { answerCommand() }
            var reusable = next.flatMap { old in old.atStart && !old.failed && reading !== old ? old : nil }
            if reusable != nil { next = nil }
            teardown()
            let slot = take(&reusable, for: track) ?? makeSlot(track)
            current = slot
            next = nextTrack.map { take(&reusable, for: $0) ?? makeSlot($0) }
            reusable.map(release)
            self.playWhenReady = playWhenReady
            readyPausedAt = nil
            beginStartTiming(
                StartupTiming(
                    source: timingSource(slot),
                    start: startMs > 0 ? .resume(seconds: Double(startMs) / 1000) : .fresh,
                    open: slot.opened || slot.preparing != nil ? .preopened : .opened,
                    playRequestedAt: requestedAt
                ),
                of: slot
            )
            setState(.loading)
            openIfNeeded(slot)
            restart(atFrame: frames(ms: startMs))
        }
    }

    /// Set, replace or clear the item after the current one. If the old next has already started
    /// to be scheduled, the scheduled audio is rebuilt from the current position.
    ///
    /// A next set once the queue's end is scheduled (the current track ran out or failed, or the
    /// old next failed to open) carries on from the last frame, and so does one set in the same
    /// breath as a load whose current track failed to open. After a track that failed has ended,
    /// the new next starts at once; after one that played out, the owner loads what follows.
    public func setNext(_ track: PlaybackTrack?) {
        engineQueue.async { [self] in
            guard let current else { return }
            // Follow the playhead into the next track, but leave concluding the end to the ticker:
            // an end not yet reported is what the new next carries on from.
            updateTimeline(concludingEnd: false)
            let old = next
            if let old, old.opened, !old.atStart || reading === old {
                // The old next is already (partly or wholly) in the node's queue behind the
                // current track, and scheduled buffers cannot be taken back: rebuild.
                release(old)
                next = track.map(makeSlot)
                restart(atFrame: currentMediaFrame())
                return
            }
            if let old { release(old) }
            next = track.map(makeSlot)
            if let old, reading === old {
                // The current track ended while the old next was still opening, and none of it is
                // scheduled: the stream carries on into the new next instead, or (released, the old
                // one reads as failed) ends.
                if let next { beginReading(next) }
                fill()
                return
            }
            // Pausing at the end: still the current track's end, unless there's no next to hold back from.
            if drained, !pausedAtEnd, state != .ended, pauseAtEnd, !current.failed {
                pausingAtEnd = next != nil
                return
            }
            guard let next else { return }
            if state == .ended {
                if current.failed { promote(next, restartingAt: 0) }
            } else if drained, reading == nil, !pausedAtEnd {
                drained = false
                // The node may have run dry already: the new next starts where its clock is.
                if buffersInFlight == 0 { beginUnderrun() }
                beginReading(next)
                fill()
            } else {
                prepareNextIfDue()
            }
        }
    }

    /// Pause on the current track's last frame instead of carrying on into the next (Media3's
    /// `pauseAtEndOfMediaItems`): the next stays next, and none of it is scheduled while this is on. Paused
    /// there, reported through ``onPausedAtEnd`` then as paused, the position is the track's end; a play
    /// starts the next. Each track pauses at its end until it's turned off. Turned off before the end is
    /// heard, the next joins the current track gaplessly after all. Kept across loads.
    public func setPauseAtEnd(_ enabled: Bool) {
        engineQueue.async { [self] in
            guard enabled != pauseAtEnd else { return }
            pauseAtEnd = enabled
            guard current != nil else { return }
            updateTimeline(concludingEnd: false)
            if enabled {
                // Some of the next may already be read behind the current track: rebuild without it.
                if let next, reading === next || (next.opened && !next.atStart) {
                    restart(atFrame: currentMediaFrame())
                }
            } else if pausingAtEnd, !pausedAtEnd {
                pausingAtEnd = false
                guard let next else { return }
                drained = false
                if buffersInFlight == 0 { beginUnderrun() }
                beginReading(next)
                fill()
            }
        }
    }

    /// A slot for `track` whose source tells the engine when a read waits on its stream (``readWaited()``).
    func makeSlot(_ track: PlaybackTrack) -> Slot {
        let slot = Slot(track: track)
        slot.source.onReadWaiting { [weak self] in self?.readWaited() }
        return slot
    }

    /// The owner's ``activateOutput``, started now on `activationQueue`; nil if there's none.
    private func beginActivation() -> OutputActivation? {
        activateOutput.map { OutputActivation(on: activationQueue, $0) }
    }

    /// `slot` (cleared) relabelled as `track`, if it's `track`'s stream.
    private func take(_ slot: inout Slot?, for track: PlaybackTrack) -> Slot? {
        guard let taken = slot, taken.track.playsSameStream(as: track) else { return nil }
        slot = nil
        taken.track = track
        return taken
    }

    /// A play was asked for above the engine (`trigger`: one token naming who asked). The next start, if it follows
    /// within ``StartupTiming/requestWindow``, is timed from here too. Call before the load or play it leads to.
    public func notePlayRequest(_ trigger: String) {
        let request = StartupTiming.PlayRequest(at: StartupTiming.now(), trigger: trigger)
        startSignposter.emitEvent("play request", "\(trigger, privacy: .public)")
        engineQueue.async { [self] in pendingPlayRequest = request }
    }

    public func play() {
        let activation = beginActivation()
        let requestedAt = StartupTiming.now()
        engineQueue.async { [self] in
            engineLog.notice("play: \(self.state.rawValue, privacy: .public)")
            commandsTaken += 1
            defer { answerCommand() }
            playWhenReady = true
            pendingActivation = activation
            guard current != nil, state != .ended else { return }
            if pausedAtEnd {
                // The current track is over: on into the next, or it's the queue's end.
                guard let next else { return setState(.ended) }
                return promote(next, restartingAt: 0)
            }
            if state != .playing { timePlay(requestedAt: requestedAt) }
            guard startPlaying() else { return stayPaused() }
        }
    }

    public func pause() {
        engineQueue.async { [self] in
            engineLog.notice("pause: \(self.state.rawValue, privacy: .public)")
            commandsTaken += 1
            defer { answerCommand() }
            playWhenReady = false
            pendingActivation = nil
            pendingPlayRequest = nil
            dropStartTiming()
            let held = playedStreamIndex()
            player.pause()
            timelineLock.withLock { timeline.held = held }
            if current != nil, state == .playing || state == .loading { setState(.paused) }
            stopTicker()
            emitPosition()
        }
    }

    /// Seek within the current track. Exact: the first frame heard is the frame at `ms`. A track
    /// whose source can't seek plays on untouched, reported through ``onSeekUnsupported``.
    public func seek(toMs ms: Int64) {
        // An interrupt is only undone by a seek, so a source that can't seek is never interrupted:
        // it would never read again.
        if let active = activeSourceLock.withLock({ activeSource }), active.isSeekable { active.interrupt() }
        engineQueue.async { [self] in
            updateTimeline()
            guard let current else { return }
            guard !current.opened || current.failed || current.source.isSeekable else {
                reportSeekUnsupported(current, ms: ms)
                resumeReadingNext()
                return
            }
            restart(atFrame: frames(ms: ms))
        }
    }

    public func stop() {
        interruptActiveRead()
        engineQueue.async { [self] in
            commandsTaken += 1
            defer { answerCommand() }
            pendingActivation = nil
            teardown()
            setState(.idle)
        }
    }

    /// Playback speed, pitch preserved. At 1× the time-pitch unit is out of the graph entirely.
    public func setSpeed(_ speed: Float) {
        engineQueue.async { [self] in
            let wasInGraph = timePitchInGraph
            timePitch.rate = speed
            guard (speed != 1) != wasInGraph else { return }
            let resumeAt = currentMediaFrame()
            player.stop()
            connectGraph(speed: speed)
            if current != nil { restart(atFrame: resumeAt) }
        }
    }

    /// `player → mainMixer` at 1×, `player → timePitch → mainMixer` otherwise.
    ///
    /// The time-pitch unit is not merely bypassed at 1×: even bypassed it pulls a whole render
    /// block (4096 frames) ahead from the player node and holds it, so the node's clock runs a
    /// block ahead of what is heard and a seek plays out the stale block first. Out of the graph,
    /// the node's samples reach the mixer untouched and its clock is the playhead.
    private func connectGraph(speed: Float) {
        engine.disconnectNodeOutput(player)
        engine.disconnectNodeOutput(timePitch)
        if speed == 1 {
            engine.connect(player, to: engine.mainMixerNode, format: format)
        } else {
            engine.connect(player, to: timePitch, format: format)
            engine.connect(timePitch, to: engine.mainMixerNode, format: format)
        }
        timePitchInGraph = speed != 1
    }

    public func setVolume(_ volume: Float) {
        engineQueue.async { [self] in player.volume = volume }
    }

    /// Takes effect from the next buffer decoded (up to `scheduleAheadSeconds` later).
    public func setEqualizer(_ settings: EqualizerSettings) {
        engineQueue.async { [self] in pendingEqualizer = settings }
    }

    /// Takes effect at the next load or seek, where the stream's alignment restarts anyway.
    public func setLimiter(_ settings: LimiterSettings) {
        engineQueue.async { [self] in pendingLimiter = settings }
    }

    /// The uid and position (ms) of what is being heard.
    public var position: (uid: String, ms: Int64)? {
        let stream = playedStreamIndex()
        return timelineLock.withLock {
            guard let segment = Self.segment(at: stream, in: timeline) else { return nil }
            return (segment.uid, ms(frames: segment.mediaStart + stream - segment.streamStart))
        }
    }

    /// The current track's duration, nil until it is open or when the container does not say.
    public var durationMs: Int64? {
        let stream = playedStreamIndex()
        return timelineLock.withLock {
            Self.segment(at: stream, in: timeline)?.durationFrames.map { ms(frames: $0) }
        }
    }
}
