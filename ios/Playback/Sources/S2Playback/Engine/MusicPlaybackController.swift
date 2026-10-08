import AVFoundation
import os

/// The engine's category, for the underruns: one
/// `log stream --predicate 'category == "audio-engine"'` shows the node running dry beside the
/// fetch that let it (#896).
private let engineLog = Logger(subsystem: "com.simplecityapps.shuttle2", category: "audio-engine")

/// One queue item as the engine sees it: an id, its ReplayGain, and where its audio comes from.
///
/// The Kotlin side (`EnginePlayerController`, phase-6-playback.md) owns the queue, shuffle and
/// repeat; it hands the engine the current item and the next one and nothing else.
public struct PlaybackTrack {
    /// This hand-over of the track, for telling reports apart: the owner gives every hand-over a new
    /// one, even of the same stream.
    public let uid: String
    /// What the track plays, for telling whether two hand-overs are the same stream: a pre-opened
    /// next the owner hands back (as the current track after a skip onto it, or as the next again)
    /// is kept when this matches. A file or HTTP(S) track's URL and headers; nil never matches. A
    /// server stream's URL names its session and transcode, so the same song resolved again (at
    /// another quality, from a position) is another stream, and is opened again.
    public let streamIdentity: String?
    /// ReplayGain in dB, already resolved (track or album mode, preamp, clipping policy) by the
    /// shared Kotlin code. 0 is unity.
    public let gainDb: Float
    /// How long the track is expected to run (the library's duration), for when its container
    /// doesn't say: a progressive transcode. Only used to time opening the track after it.
    public let expectedDurationMs: Int64?
    public let makeSource: () -> TrackPCMSource

    public init(
        uid: String,
        streamIdentity: String? = nil,
        gainDb: Float = 0,
        expectedDurationMs: Int64? = nil,
        makeSource: @escaping () -> TrackPCMSource
    ) {
        self.uid = uid
        self.streamIdentity = streamIdentity
        self.gainDb = gainDb
        self.expectedDurationMs = expectedDurationMs
        self.makeSource = makeSource
    }

    /// A file or HTTP(S) URL decoded by FFmpeg.
    public init(
        uid: String,
        url: URL,
        headers: [String: String] = [:],
        gainDb: Float = 0,
        expectedDurationMs: Int64? = nil
    ) {
        let identity = ([url.absoluteString] + headers.sorted { $0.key < $1.key }.map { "\($0.key): \($0.value)" })
            .joined(separator: "\n")
        self.init(uid: uid, streamIdentity: identity, gainDb: gainDb, expectedDurationMs: expectedDurationMs) {
            FFmpegTrackSource(url: url, headers: headers)
        }
    }

    /// Whether `other` is a hand-over of the same stream.
    func playsSameStream(as other: PlaybackTrack) -> Bool {
        streamIdentity != nil && streamIdentity == other.streamIdentity
    }
}

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
    /// How long a start that failed after a route change waits before it's tried again, and how many
    /// times it is (#715).
    var startRetryDelay: DispatchTimeInterval = .milliseconds(500)
    static let startRetryAttempts = 4
    private let player = AVAudioPlayerNode()
    private let timePitch = AVAudioUnitTimePitch()
    let format: AVAudioFormat
    private let renderingMode: RenderingMode
    private let engineQueue = DispatchQueue(label: "com.simplecityapps.shuttle2.playback.engine", qos: .userInitiated)
    /// Set on `engineQueue`, so a source's wait hook can tell it's called inside the engine's own read.
    private static let engineQueueKey = DispatchSpecificKey<Bool>()
    /// Where next tracks are opened. Concurrent: a cancelled open may still be unwinding.
    private let prepareQueue = DispatchQueue(
        label: "com.simplecityapps.shuttle2.playback.prepare", qos: .userInitiated, attributes: .concurrent
    )
    /// Every open in flight on `prepareQueue`, for the tests to wait on.
    private let opening = DispatchGroup()
    /// Where ``activateOutput`` runs. Serial: activations finish in the order the plays were made.
    private let activationQueue = DispatchQueue(label: "com.simplecityapps.shuttle2.playback.activation", qos: .userInitiated)
    private let callbackQueue: DispatchQueue

    /// How far ahead of the playhead audio is decoded and scheduled. Also how late an EQ change is
    /// heard, so it is short; the byte source's own read-ahead is what rides out the network.
    private let scheduleAheadFrames: Int64
    /// How long before the current track's end the next is opened. See the class doc.
    private var preopenLead: PreopenLead
    /// How much of a current track of unknown length is read before the next is opened.
    private let steadyFrames: Int64
    private static let chunkFrames = 4096
    /// How much is scheduled before a play starts the node; the rest of `scheduleAheadFrames` is decoded
    /// while the first of it plays (#687).
    private static let startFrames = Int64(chunkFrames * 2)

    // MARK: Engine-queue state

    private final class Slot {
        private static var lastId = 0
        /// Unique for the controller's life (engine queue only), unlike an object address.
        let id: Int
        /// Replaced by a load that hands the same stream back, taking the slot over.
        var track: PlaybackTrack
        let source: TrackPCMSource
        var opened = false
        /// Opened and neither read nor sought since: the source is at its first frame (or, primed,
        /// just past `primed`).
        var atStart = true
        /// It could not be opened, or a read failed.
        var failed = false
        var durationFrames: Int64?
        /// Non-nil while the source is being opened on the prepare queue; left when it's done,
        /// with `prepared` set. Nothing else touches the source until then.
        var preparing: DispatchGroup?
        /// What the open found (the duration), the first frames it decoded and how long that took,
        /// or its error. Written on the prepare queue before `preparing` is left; applied on the
        /// engine queue.
        var prepared: Result<(duration: Int64?, primed: [Float], primeError: Error?, seconds: Double), Error>?
        /// Decoded ahead by the open, read before the source: interleaved, at the output format.
        var primed: [Float] = []
        /// The open's read failed: the first read fails with it, as it would have.
        var primeError: Error?

        init(track: PlaybackTrack) {
            Self.lastId += 1
            id = Self.lastId
            self.track = track
            self.source = track.makeSource()
        }
    }

    private var current: Slot?
    private var next: Slot?
    /// The slot frames are being read from: `current`, then `next` once current's source ended.
    private var reading: Slot?
    private var playWhenReady = false
    /// The activation the last play (or load that plays) asked for, until the engine starts on it or a pause,
    /// paused load or stop drops it.
    private var pendingActivation: OutputActivation?
    private var state: State = .idle
    /// Commands taken so far, stamped on each state report (``onStateChanged``).
    private var commandsTaken = 0
    /// `commandsTaken` when the last state report was made: a command with no report of its own is answered.
    private var commandsReported = 0
    private let processor: PCMProcessor
    private var pendingEqualizer: EqualizerSettings?
    private var pendingLimiter: LimiterSettings?
    /// Frames fed to the processor since the last restart. Output frame n is input frame n
    /// (``PCMProcessor`` hides the limiter's delay), so this is also where the next segment starts.
    private var inputIndex: Int64 = 0
    /// Frames scheduled on the node since the last restart.
    private var outputIndex: Int64 = 0
    private var buffersInFlight = 0
    private var starved = false
    /// The node ran dry while playing — an underrun, not the queue's end: when (``StartupTiming/now()``)
    /// and where in the current track. Logged as it starts and as it ends (#896). The state stays
    /// playing, but the owner hears loading for as long as it lasts, and playing again once a buffer
    /// reaches the node, so the listener sees the buffering (#897). A restart (a seek, a rebuild) keeps
    /// it: the listener hears silence until the restarted stream's first buffer.
    private var underrun: (since: TimeInterval, ms: Int64)?
    /// The last buffer health line: when, and whether it was under `lowBufferSeconds`.
    private var lastHealthLog: (at: TimeInterval, low: Bool)?
    private static let healthLogSeconds: TimeInterval = 10
    private static let lowBufferSeconds: Double = 5
    /// The queue's last frame is scheduled (current ended with no next).
    private var drained = false
    /// Bumped by every restart; completions from buffers a restart discarded are ignored.
    private var generation = 0
    private var scratch: [Float]
    private var ticker: DispatchSourceTimer?
    private var timePitchInGraph = false
    /// The start being timed: from a load or play to the node's first rendered frame (#687).
    private var startTiming: StartupTiming?
    /// Tells a render watch for an earlier start to stop.
    private var startTimingSerial = 0
    /// When the current load was ready, paused: a play close behind it is still the load's start.
    private var readyPausedAt: TimeInterval?
    /// The last start's record, once its line was logged.
    private var lastStartTiming: StartupTiming?

    // MARK: Shared state (any thread, under `timelineLock`)

    struct Segment {
        let streamStart: Int64
        let uid: String
        let mediaStart: Int64
        let durationFrames: Int64?
        let slot: Int
    }

    struct Anchor {
        let stream: Int64
        let player: Int64
    }

    struct Timeline {
        var segments: [Segment] = []
        var anchors: [Anchor] = [Anchor(stream: 0, player: 0)]
        /// Frames scheduled so far; the playhead never passes it.
        var scheduledEnd: Int64 = 0
        /// Non-nil while the node is not rendering (stopped, paused, not yet started).
        var held: Int64? = 0
        /// The last stream index known to be heard: where the node was released from `held`, then
        /// each tick's reading. The answer while the node's clock can't be read: before it first
        /// renders after a release, and once a route change has stopped the engine under it (#714).
        var heard: Int64 = 0

        /// The stream index being heard, given the node's clock (`nodeTime`, nil when it has no
        /// valid render time).
        func playedStreamIndex(nodeTime: Int64?) -> Int64 {
            if let held { return held }
            guard let now = nodeTime else { return heard }
            var stream: Int64 = 0
            for (i, anchor) in anchors.enumerated() where anchor.player <= now {
                stream = anchor.stream + (now - anchor.player)
                // A starved node's clock ran on; the stream did not.
                if i + 1 < anchors.count { stream = min(stream, anchors[i + 1].stream) }
            }
            return max(0, min(stream, scheduledEnd))
        }
    }

    private let timelineLock = NSLock()
    private var timeline = Timeline()
    private let activeSourceLock = NSLock()
    private var activeSource: TrackPCMSource?
    /// Configuration changes posted so far (any thread). A route change can post several in a row;
    /// only the last one queued rebuilds (#813).
    private let configurationChangeLock = NSLock()
    private var configurationChanges = 0

    private struct Callbacks {
        var state: ((State, String?, Int) -> Void)?
        var transition: ((String) -> Void)?
        var failed: ((String, Error) -> Void)?
        var position: ((String, Int64) -> Void)?
        var seekUnsupported: ((String, Int64) -> Void)?
        var activateOutput: (() -> Bool)?
    }

    /// One ``activateOutput`` call in flight on `activationQueue`.
    private final class OutputActivation {
        private let done = DispatchGroup()
        /// Written before `done` is left.
        private var activated = true
        /// When `activate` returned (``StartupTiming/now()``). Written before `done` is left.
        private(set) var activatedAt: TimeInterval?

        init(on queue: DispatchQueue, _ activate: @escaping () -> Bool) {
            done.enter()
            queue.async { [self] in
                activated = activate()
                activatedAt = StartupTiming.now()
                done.leave()
            }
        }

        /// Whether the output was readied, once it's done.
        func wait() -> Bool {
            done.wait()
            return activated
        }
    }

    private let callbackLock = NSLock()
    private var callbacks = Callbacks()

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
            guard let next else { return }
            if state == .ended {
                if current.failed { promote(next, restartingAt: 0) }
            } else if drained, reading == nil {
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

    /// A slot for `track` whose source tells the engine when a read waits on its stream (``readWaited()``).
    private func makeSlot(_ track: PlaybackTrack) -> Slot {
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

    // MARK: - Testing

    /// Offline mode: render the next `frameCount` frames of the mixer's output.
    func renderOffline(frameCount: AVAudioFrameCount) throws -> AVAudioPCMBuffer {
        guard let buffer = AVAudioPCMBuffer(pcmFormat: engine.manualRenderingFormat, frameCapacity: frameCount) else {
            throw TrackSourceError.failed("render buffer")
        }
        let status = try engine.renderOffline(frameCount, to: buffer)
        guard status == .success else { throw TrackSourceError.failed("render status \(status.rawValue)") }
        return buffer
    }

    /// What the position ticker does, synchronously: top the node's queue up and follow the
    /// playhead (transitions, the end, position). Then, `awaitingOpens`, waits for the next track's
    /// open if one is in flight, as if it were instant; and for the callbacks it all caused.
    func pumpForTesting(awaitingOpens: Bool = true) {
        engineQueue.sync {
            fill()
            updateTimeline()
        }
        if awaitingOpens { awaitOpensForTesting() }
        callbackQueue.sync {}
    }

    /// Runs `body` on the engine queue: whatever it asks of the controller waits until it returns.
    func onEngineQueueForTesting(_ body: () -> Void) {
        engineQueue.sync(execute: body)
    }

    /// The last start's `ttfa` record, once its line was logged.
    var lastStartTimingForTesting: StartupTiming? { engineQueue.sync { lastStartTiming } }

    /// Wait for everything already asked of the controller, a next track's open included.
    func syncForTesting() {
        engineQueue.sync {}
        awaitOpensForTesting()
        callbackQueue.sync {}
    }

    /// Whether a track's open (or its first chunk's decode) is still running on the prepare queue.
    var hasOpenInFlightForTesting: Bool { opening.wait(timeout: .now()) == .timedOut }

    /// An open queues its result on the engine queue before it leaves `opening`.
    private func awaitOpensForTesting() {
        opening.wait()
        engineQueue.sync {}
    }

    // MARK: - Engine queue

    private func frames(ms: Int64) -> Int64 { ms * Int64(outputSampleRate) / 1000 }

    private func ms(frames: Int64) -> Int64 { frames * 1000 / Int64(outputSampleRate) }

    private func setState(_ newState: State) {
        guard newState != state else { return }
        engineLog.notice(
            "state \(self.state.rawValue, privacy: .public) -> \(newState.rawValue, privacy: .public) uid \(self.current?.track.uid ?? "-", privacy: .public)"
        )
        state = newState
        if newState == .playing { lastHealthLog = nil }
        if newState == .paused || newState == .idle || newState == .ended {
            endUnderrun(newState.rawValue)
            pauseEngine()
        }
        reportState(newState)
    }

    /// The current track's state, on the callback queue. Playing is loading while an underrun lasts: a play, a
    /// restart's start, a command answered then doesn't end the buffering the listener hears (#897).
    private func reportState(_ newState: State) {
        let newState = newState == .playing && underrun != nil ? .loading : newState
        let uid = current?.track.uid
        let commands = commandsTaken
        commandsReported = commands
        let callback = callbackLock.withLock { callbacks.state }
        if let callback { callbackQueue.async { callback(newState, uid, commands) } }
    }

    /// A command that changed nothing `setState` reports (a play while playing, a pause while paused, a play
    /// refused while paused) still has its answer: the state it left, at its count. Otherwise the owner would
    /// only have reports from before it, which it takes as superseded, and never hear where the engine is. The
    /// end of a track isn't repeated: it's an event, said once, and nothing but a load moves the engine on.
    private func answerCommand() {
        guard commandsReported < commandsTaken, state != .ended else { return }
        reportState(state)
    }

    private func reportFailure(_ slot: Slot, _ error: Error) {
        slot.failed = true
        // The description can carry a URL and its query: only the domain and code are public.
        let nsError = error as NSError
        engineLog.error(
            "track \(slot.track.uid, privacy: .public) failed: \(nsError.domain, privacy: .public) \(nsError.code) \(String(describing: error), privacy: .private)"
        )
        let uid = slot.track.uid
        let callback = callbackLock.withLock { callbacks.failed }
        if let callback { callbackQueue.async { callback(uid, error) } }
    }

    private func reportSeekUnsupported(_ slot: Slot, ms: Int64) {
        engineLog.info("track \(slot.track.uid, privacy: .public) can't seek; reporting \(ms) ms")
        let uid = slot.track.uid
        let callback = callbackLock.withLock { callbacks.seekUnsupported }
        if let callback { callbackQueue.async { callback(uid, ms) } }
    }

    /// A seek that didn't happen may have interrupted the next track, already being read behind an
    /// unseekable current one. Sought to exactly where it was read to, it carries on seamlessly. One
    /// that refuses the seek (``TrackSourceError/unseekable``) can't carry on, and isn't the current
    /// track the owner could re-open at a position: it failed.
    private func resumeReadingNext() {
        guard let slot = reading, slot !== current, slot.opened, !slot.failed else { return }
        let streamStart = timelineLock.withLock { timeline.segments.last { $0.slot == slot.id }?.streamStart }
        guard let streamStart else { return }
        do {
            try seek(slot, toFrame: inputIndex - streamStart)
        } catch {
            reportFailure(slot, error)
        }
        fill()
    }

    private func releaseHold() {
        timelineLock.withLock {
            timeline.heard = timeline.held ?? timeline.heard
            timeline.held = nil
        }
    }

    private func interruptActiveRead() {
        activeSourceLock.withLock { activeSource }?.interrupt()
    }

    /// A source still opening isn't exposed as active: its open must not be interrupted (a seek of
    /// the current track would fail it), and a load or stop releases it anyway.
    private func setReading(_ slot: Slot?) {
        reading = slot
        let source = slot?.preparing == nil ? slot?.source : nil
        activeSourceLock.withLock { activeSource = source }
    }

    /// Stops the output while nothing plays. A running engine renders silence, and iOS takes an app
    /// whose output runs for one that is playing: the lock screen and Control Center would show it
    /// playing, with a pause button, after it paused or stopped (#691). `startEngineIfNeeded` starts
    /// it again before the node plays.
    /// Paused, it's prepared again, so the next play's start has nothing to allocate (#687).
    private func pauseEngine() {
        guard case .realtime = renderingMode, engine.isRunning else { return }
        engine.pause()
        engine.prepare()
    }

    /// False (logged) if the owner refused to ready the output (``activateOutput``), or the engine is
    /// stopped and won't start: the audio session couldn't be activated, in a call or with another app
    /// holding the hardware. The node must not play then; on a stopped engine it raises.
    private func startEngineIfNeeded() -> Bool {
        if let activation = pendingActivation {
            pendingActivation = nil
            if startTiming?.nodePlayedAt == nil { startTiming?.sessionAwaitedAt = StartupTiming.now() }
            let activated = activation.wait()
            if startTiming?.nodePlayedAt == nil { startTiming?.sessionActivatedAt = activation.activatedAt }
            guard activated else {
                engineLog.error("output not activated; paused")
                return false
            }
        }
        guard !engine.isRunning else { return true }
        do {
            let started = StartupTiming.now()
            try startEngine(engine)
            if startTiming?.nodePlayedAt == nil { startTiming?.engineStartMs = Int(((StartupTiming.now() - started) * 1000).rounded()) }
            return true
        } catch {
            engineLog.error("engine start failed: \(String(describing: error), privacy: .public)")
            return false
        }
    }

    /// Starts the engine and plays the node from where the stream is; false, with nothing played, if
    /// the engine won't start. The node starts on `startFrames` and the rest is decoded as it plays.
    private func startPlaying() -> Bool {
        guard startEngineIfNeeded() else { return false }
        fill(aheadFrames: Self.startFrames)
        playNode()
        releaseHold()
        setState(.playing)
        startTicker()
        fill()
        return true
    }

    /// A route change stopped the engine and it wouldn't start again while the route settled (#715):
    /// loading, the start is tried again up to `startRetryAttempts` times, `startRetryDelay` apart. A
    /// pause, play, load, seek or stop meanwhile drops it; the last failure stays paused, as a play
    /// the engine can't start does.
    private func retryStart(attempt: Int = 1) {
        stopTicker()
        setState(.loading)
        let generation = self.generation
        engineQueue.asyncAfter(deadline: .now() + startRetryDelay) { [weak self] in
            guard let self, self.generation == generation, playWhenReady, state == .loading else { return }
            if startPlaying() {
                engineLog.notice("engine started on retry \(attempt)")
            } else if attempt < Self.startRetryAttempts {
                retryStart(attempt: attempt + 1)
            } else {
                engineLog.error("engine didn't start after \(attempt) retries; paused")
                stayPaused()
            }
        }
    }

    /// A play the engine couldn't start: paused, as if it had been asked to pause, so the owner and
    /// Now Playing show it paused. Nothing plays until the next play. A play refused while already
    /// paused changes no state, and is answered with paused all the same (``answerCommand()``).
    /// Not a decode failure: the track stays put.
    private func stayPaused() {
        playWhenReady = false
        dropStartTiming()
        stopTicker()
        setState(.paused)
    }

    /// Opens `slot`'s source, or waits for the open already under way; false (after reporting it)
    /// if it could not be.
    @discardableResult
    private func openIfNeeded(_ slot: Slot) -> Bool {
        if let preparing = slot.preparing {
            preparing.wait()
            settlePrepared(slot)
        }
        guard !slot.opened, !slot.failed else { return slot.opened }
        do {
            let started = DispatchTime.now().uptimeNanoseconds
            slot.durationFrames = try slot.source.open(sampleRate: outputSampleRate, channelCount: outputChannelCount)
            slot.opened = true
            preopenLead.record(openSeconds: Self.seconds(since: started))
            return true
        } catch {
            reportFailure(slot, error)
            return false
        }
    }

    /// Open the next track off the engine queue once the current one is within the pre-open lead
    /// of its end (read, not heard: the stream reaches the join a schedule-ahead before the ear).
    /// When neither the container nor the track's expected duration says where the end is, once
    /// `steadyFrames` of it have been read since the stream last started.
    private func prepareNextIfDue() {
        guard let current, let next, !next.opened, !next.failed, next.preparing == nil else { return }
        if reading === current, current.opened, !current.failed,
           let segment = timelineLock.withLock({ timeline.segments.last { $0.slot == current.id } }) {
            let streamed = inputIndex - segment.streamStart
            if let duration = current.durationFrames ?? current.track.expectedDurationMs.map({ frames(ms: $0) }) {
                let lead = Int64(preopenLead.seconds * outputSampleRate)
                guard duration - (segment.mediaStart + streamed) <= lead else { return }
            } else {
                guard streamed >= steadyFrames else { return }
            }
        }
        prepare(next)
    }

    private static func seconds(since uptimeNanoseconds: UInt64) -> Double {
        Double(DispatchTime.now().uptimeNanoseconds - uptimeNanoseconds) / 1e9
    }

    /// Start opening `slot` on the prepare queue, and decoding its first chunk, so its first read
    /// doesn't wait on the network either.
    private func prepare(_ slot: Slot) {
        guard !slot.opened, !slot.failed, slot.preparing == nil else { return }
        let group = DispatchGroup()
        group.enter()
        opening.enter()
        slot.preparing = group
        let source = slot.source
        let sampleRate = outputSampleRate
        let channels = outputChannelCount
        let chunkFrames = Self.chunkFrames
        prepareQueue.async { [weak self] in
            let started = DispatchTime.now().uptimeNanoseconds
            slot.prepared = Result {
                let duration = try source.open(sampleRate: sampleRate, channelCount: channels)
                var primed = [Float](repeating: 0, count: chunkFrames * channels)
                var primeError: Error?
                do {
                    let got = try primed.withUnsafeMutableBufferPointer {
                        try source.read(into: $0.baseAddress!, maxFrames: chunkFrames)
                    }
                    primed.removeSubrange((got * channels)...)
                } catch {
                    primed = []
                    primeError = error
                }
                return (duration: duration, primed: primed, primeError: primeError, seconds: Self.seconds(since: started))
            }
            group.leave()
            if let self {
                engineQueue.async { self.finishPrepare(slot) }
                opening.leave()
            }
        }
    }

    /// `slot`'s open is done: apply it, and carry the stream on into it if it was waiting there.
    private func finishPrepare(_ slot: Slot) {
        // Settled already by a wait for it, or released.
        guard slot.preparing != nil else { return }
        settlePrepared(slot)
        guard reading === slot else { return }
        setReading(slot)
        if slot.opened { appendSegment(for: slot, mediaStart: 0) }
        fill()
    }

    private func settlePrepared(_ slot: Slot) {
        guard slot.preparing != nil else { return }
        slot.preparing = nil
        switch slot.prepared {
        case let .success(result):
            slot.durationFrames = result.duration
            slot.primed = result.primed
            slot.primeError = result.primeError
            slot.opened = true
            preopenLead.record(openSeconds: result.seconds)
        case let .failure(error):
            reportFailure(slot, error)
        case nil:
            break
        }
        slot.prepared = nil
    }

    /// Let go of a slot that is no longer current or next. One still opening is cancelled and
    /// marked failed, silently, so a stream waiting on it reads it as ended.
    private func release(_ slot: Slot) {
        slot.source.cancel()
        if slot.preparing != nil {
            slot.preparing = nil
            slot.failed = true
        }
    }

    private func seek(_ slot: Slot, toFrame frame: Int64) throws {
        slot.primed = []
        slot.primeError = nil
        slot.atStart = false
        try slot.source.seek(toFrame: frame)
    }

    /// Drop everything scheduled and start the stream again at `frame` of the current track.
    /// `retryingStart`: an engine that won't start is tried again (``retryStart(attempt:)``) rather than
    /// left paused at once.
    private func restart(atFrame frame: Int64, retryingStart: Bool = false) {
        guard let current else { return }
        engineLog.notice("restart at \(frame) frames, playWhenReady \(self.playWhenReady)")
        generation += 1
        player.stop()
        // What the time-pitch unit already pulled belongs to the old stream.
        if timePitchInGraph { timePitch.reset() }
        buffersInFlight = 0
        starved = false
        // An underrun carries on until the restarted stream's first buffer; one that's no longer played ends.
        if !playWhenReady { endUnderrun("restarted") }
        drained = false
        inputIndex = 0
        outputIndex = 0
        if let pendingLimiter {
            processor.setLimiter(pendingLimiter)
            self.pendingLimiter = nil
        }
        processor.reset()
        // A next that had started to be read is re-opened from its start when it is reached again.
        // One opened (or opening) and not read yet is still at its start, and is kept.
        if let old = next, old.opened, !old.atStart {
            release(old)
            next = makeSlot(old.track)
        }
        timelineLock.withLock { timeline = Timeline() }
        setReading(current)
        var startFrame = frame
        // S2: a source still at its first frame is not sought to frame 0. The decoder's start trims
        // the encoder delay (an MP4's edit list, Opus pre-skip); FFmpeg's seek to the start of an
        // AAC-in-MP4 track does not, and ~2,100 frames of priming would open every load.
        if current.opened, !current.failed, current.source.isSeekable, !(frame == 0 && current.atStart) {
            do {
                try seek(current, toFrame: frame)
            } catch TrackSourceError.unseekable {
                // Refused (an estimated length the stream can't serve): unseekable from now on, below.
            } catch {
                reportFailure(current, error)
                startFrame = 0
            }
        }
        if current.opened, !current.failed, !current.source.isSeekable, !(frame == 0 && current.atStart) {
            // A progressive transcode: it plays on from where it was read to, and the owner is told
            // so it can re-open the stream at the frame. After a load that is its start; after a
            // speed or output change it is about where it was heard. One that refused the seek
            // reads nothing more (``readChunk()``) until the owner re-opens it.
            if current.atStart { startFrame = 0 }
            reportSeekUnsupported(current, ms: ms(frames: frame))
        }
        appendSegment(for: current, mediaStart: startFrame)
        if startTiming?.positionedAt == nil { startTiming?.positionedAt = StartupTiming.now() }
        if !playWhenReady {
            pendingActivation = nil
            fill()
            readyPausedAt = StartupTiming.now()
            setState(.paused)
        } else if !startPlaying() {
            if retryingStart { retryStart() } else { stayPaused() }
        }
        emitPosition()
    }

    /// Carry on from the current track into `slot`, the next: at once if it's open, else once its
    /// open (started now, if it wasn't already) is done; ``readChunk()`` waits for it. A next that
    /// can't be opened gets no segment, so it is never transitioned into: the current track ends,
    /// and the owner, told of the failure, decides what follows it.
    private func beginReading(_ slot: Slot) {
        prepare(slot)
        setReading(slot)
        if slot.opened { appendSegment(for: slot, mediaStart: 0) }
    }

    /// Make `slot` (the next) the current track, report the transition, and start the stream at
    /// `frame` of it.
    private func promote(_ slot: Slot, restartingAt frame: Int64) {
        current.map(release)
        current = slot
        next = nil
        reportTransition(to: slot, gapless: false)
        openIfNeeded(slot)
        restart(atFrame: frame)
    }

    private func reportTransition(to slot: Slot, gapless: Bool) {
        let uid = slot.track.uid
        engineLog.notice("transition to uid \(uid, privacy: .public), \(gapless ? "gapless" : "restarted", privacy: .public)")
        let callback = callbackLock.withLock { callbacks.transition }
        if let callback { callbackQueue.async { callback(uid) } }
    }

    private func appendSegment(for slot: Slot, mediaStart: Int64) {
        let segment = Segment(
            streamStart: inputIndex, uid: slot.track.uid, mediaStart: mediaStart,
            durationFrames: slot.durationFrames, slot: slot.id
        )
        timelineLock.withLock { timeline.segments.append(segment) }
    }

    private func teardown() {
        generation += 1
        dropStartTiming()
        player.stop()
        stopTicker()
        current.map(release)
        next.map(release)
        current = nil
        next = nil
        setReading(nil)
        drained = false
        buffersInFlight = 0
        endUnderrun("stopped")
        timelineLock.withLock { timeline = Timeline() }
    }

    /// Decode and schedule until `aheadFrames` are queued ahead of the playhead or the queue's end is
    /// scheduled.
    private func fill(aheadFrames: Int64? = nil) {
        let aheadFrames = aheadFrames ?? scheduleAheadFrames
        guard current != nil else { return }
        prepareNextIfDue()
        if let pendingEqualizer {
            processor.setEqualizer(pendingEqualizer)
            self.pendingEqualizer = nil
        }
        while !drained, outputIndex - playedStreamIndex() < aheadFrames {
            guard let buffer = readChunk() else { return }
            if buffer.frameLength > 0 { schedule(buffer) }
        }
    }

    /// Read up to one chunk from `reading`, crossing into `next` where the current track ends.
    /// nil when nothing could be read (an interrupted read, the next track still opening, or
    /// nothing left).
    private func readChunk() -> AVAudioPCMBuffer? {
        var output: [Float] = []
        output.reserveCapacity(scratch.count)
        var filled = 0
        let channels = outputChannelCount
        var interrupted = false
        scratch.withUnsafeMutableBufferPointer { raw in
            let base = raw.baseAddress!
            while filled < Self.chunkFrames, let slot = reading {
                // Still opening: what's read so far goes out, and its open's end resumes the fill.
                // The current track's last frames, still inside the limiter, go out too: they'd
                // otherwise be heard after the wait.
                if slot.preparing != nil {
                    processor.drain(into: &output)
                    return
                }
                var got = 0
                if !slot.primed.isEmpty {
                    slot.atStart = false
                    got = min(slot.primed.count / channels, Self.chunkFrames - filled)
                    slot.primed.withUnsafeBufferPointer {
                        (base + filled * channels).update(from: $0.baseAddress!, count: got * channels)
                    }
                    slot.primed.removeFirst(got * channels)
                } else if let error = slot.primeError {
                    slot.atStart = false
                    slot.primeError = nil
                    reportFailure(slot, error)
                } else if slot.opened {
                    slot.atStart = false
                    do {
                        got = try slot.source.read(into: base + filled * channels, maxFrames: Self.chunkFrames - filled)
                    } catch TrackSourceError.interrupted {
                        interrupted = true
                        return
                    } catch TrackSourceError.unseekable where slot === current {
                        // It refused a seek, reported as unsupported: neither ended nor failed, it
                        // waits, as an interrupted read does, for the owner to re-open it.
                        interrupted = true
                        return
                    } catch {
                        reportFailure(slot, error)
                    }
                }
                if got > 0 {
                    processor.process(base + filled * channels, frameCount: got,
                                      gain: PCMProcessor.linear(db: slot.track.gainDb), into: &output)
                    filled += got
                    inputIndex += Int64(got)
                    continue
                }
                // The slot ended (or failed, or never opened): on into the next, or the queue's end.
                if slot === current, let next {
                    beginReading(next)
                } else {
                    setReading(nil)
                    processor.drain(into: &output)
                    drained = true
                }
            }
        }
        if interrupted, output.isEmpty { return nil }
        let frameCount = output.count / channels
        if frameCount == 0 {
            guard drained else { return nil }
            // Nothing more will reach the node: a dry node is the queue's end, not a stall.
            starved = false
            endUnderrun("drained")
            return AVAudioPCMBuffer(pcmFormat: format, frameCapacity: 1)
        }
        guard let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(frameCount)),
              let channelData = buffer.floatChannelData
        else { return nil }
        buffer.frameLength = AVAudioFrameCount(frameCount)
        output.withUnsafeBufferPointer { interleaved in
            for c in 0..<channels {
                let destination = channelData[c]
                for f in 0..<frameCount { destination[f] = interleaved[f * channels + c] }
            }
        }
        return buffer
    }

    private func schedule(_ buffer: AVAudioPCMBuffer) {
        if starved {
            // The node ran dry and played silence the stream does not contain; this buffer starts
            // wherever the node's clock is now.
            starved = false
            if let now = nodeSampleTime() {
                let anchor = Anchor(stream: outputIndex, player: now)
                timelineLock.withLock { timeline.anchors.append(anchor) }
            }
        }
        if underrun != nil {
            endUnderrun("recovered")
            // The one place an underrun's end says playing: a buffer is at the node. A start still to come says it.
            if current != nil, state == .playing { reportState(.playing) }
        }
        noteFirstBuffer()
        let generation = self.generation
        buffersInFlight += 1
        player.scheduleBuffer(buffer, at: nil, options: [], completionCallbackType: .dataConsumed) { [weak self] _ in
            self?.engineQueue.async {
                guard let self, self.generation == generation else { return }
                self.buffersInFlight -= 1
                // A stopped engine's node plays nothing: it hands its buffers back unplayed. A route change
                // stops the engine before it's reported, so those completions can reach this queue ahead of
                // the rebuild; they're not an underrun (#944). Only this queue starts the engine, and a
                // restart's `generation` drops anything handed back before it.
                if self.buffersInFlight == 0, !self.drained, self.state == .playing, self.engine.isRunning {
                    self.beginUnderrun()
                }
                self.fill()
            }
        }
        outputIndex += Int64(buffer.frameLength)
        let end = outputIndex
        timelineLock.withLock { timeline.scheduledEnd = end }
    }

    /// The node played its last buffer with the stream not over: from here it renders silence the
    /// stream doesn't contain, until a buffer is scheduled. The one place `starved` is set, so every
    /// underrun heard while playing is said once as it starts and once as it ends (#896); one found
    /// while paused is nobody's silence, and only marks the anchor.
    private func beginUnderrun() {
        starved = true
        guard underrun == nil, state == .playing else { return }
        let atMs = ms(frames: currentMediaFrame())
        underrun = (StartupTiming.now(), atMs)
        engineLog.warning("underrun: starved at \(atMs) ms uid \(self.current?.track.uid ?? "-", privacy: .public)")
        reportState(.loading)
    }

    /// A read of the stream has waited a while for its bytes. Called on the reading thread: on the engine queue
    /// that's inside `fill`, which a stalled stream blocks, so the node's own completions can't say it ran dry
    /// until the bytes are back. Once the node has played everything scheduled, the underrun starts here (#897).
    /// A node still held (a restart's first fill, before it plays) isn't rendering anything: a seek waiting on its
    /// stream isn't an underrun.
    private func readWaited() {
        guard DispatchQueue.getSpecific(key: Self.engineQueueKey) == true else { return }
        guard state == .playing, !drained, !starved else { return }
        let (scheduledEnd, held) = timelineLock.withLock { (timeline.scheduledEnd, timeline.held) }
        guard held == nil, playedStreamIndex() >= scheduledEnd else { return }
        beginUnderrun()
    }

    /// The underrun is over: `how` is "recovered" when a buffer reached the node, else what dropped
    /// it (a pause, a stop, the queue's end). Logs how long the listener heard silence; reports nothing,
    /// which is the caller's to say.
    private func endUnderrun(_ how: String) {
        guard let underrun else { return }
        self.underrun = nil
        let starvedMs = Int(((StartupTiming.now() - underrun.since) * 1000).rounded())
        engineLog.notice("underrun: \(how, privacy: .public) after \(starvedMs) ms, starved at \(underrun.ms) ms")
    }

    /// Follow the playhead: promote the next track once it is being heard, notice the end.
    private func updateTimeline(concludingEnd: Bool = true) {
        guard current != nil else { return }
        noteFirstRender()
        let stream = playedStreamIndex()
        // Recorded on the engine queue only, where a restart can't replace the timeline meanwhile.
        let segment = timelineLock.withLock {
            timeline.heard = stream
            return Self.segment(at: stream, in: timeline)
        }
        if let segment, let next, segment.slot == next.id {
            let old = current
            current = next
            self.next = nil
            if reading === old { setReading(next) }
            old.map(release)
            timelineLock.withLock {
                timeline.segments.removeAll { $0.streamStart < segment.streamStart }
            }
            reportTransition(to: next, gapless: true)
        }
        if concludingEnd, drained, state == .playing, stream >= outputIndex {
            player.stop()
            timelineLock.withLock { timeline.held = outputIndex }
            stopTicker()
            setState(.ended)
        }
        if state == .playing { emitPosition() }
    }

    // MARK: - Start timing (engine queue)

    private func timingSource(_ slot: Slot) -> StartupTiming.Source {
        (slot.source as? FFmpegTrackSource)?.isStreamed == true ? .streamed : .file
    }

    private func beginStartTiming(_ timing: StartupTiming, of slot: Slot) {
        startTimingSerial += 1
        startTiming = timing
        startTiming?.origin = (slot.source as? FFmpegTrackSource)?.streamOrigin
    }

    /// A start that won't reach the ear (paused, refused, torn down) has no line.
    private func dropStartTiming() {
        startTimingSerial += 1
        startTiming = nil
    }

    /// A play of a paused track. Close behind its load being ready (a load made paused, then played
    /// as it completes) it's still the load's start, timed from the load; otherwise a start of its
    /// own, of a track already open.
    private func timePlay(requestedAt: TimeInterval) {
        if let ready = readyPausedAt, startTiming?.nodePlayedAt == nil, startTiming != nil,
           requestedAt - ready < Self.playAfterReadyWindow {
            startTiming?.playAfterReadyMs = Int((max(requestedAt - ready, 0) * 1000).rounded())
            return
        }
        guard let current else { return }
        let ms = position?.ms ?? 0
        beginStartTiming(
            StartupTiming(
                source: timingSource(current),
                start: ms > 0 ? .resume(seconds: Double(ms) / 1000) : .fresh,
                open: .preopened,
                playRequestedAt: requestedAt
            ),
            of: current
        )
    }

    /// How soon after a paused load's ready a play still counts as that load's start.
    private static let playAfterReadyWindow: TimeInterval = 0.5
    /// How long the render watch polls the node's clock before giving up.
    private static let renderWatchSeconds: TimeInterval = 2

    private func noteFirstBuffer() {
        guard startTiming != nil, startTiming?.firstBufferScheduledAt == nil else { return }
        startTiming?.firstBufferScheduledAt = StartupTiming.now()
        if let stats = (current?.source as? FFmpegTrackSource)?.openStats { startTiming?.apply(stats) }
    }

    private func playNode() {
        player.play()
        guard startTiming != nil, startTiming?.nodePlayedAt == nil else { return }
        let now = StartupTiming.now()
        startTiming?.nodePlayedAt = now
        watchFirstRender(serial: startTimingSerial, deadline: now + Self.renderWatchSeconds)
    }

    /// Polls the node's clock until it moves, so `render` is the output's own latency and not the
    /// position tick's. Offline rendering is noticed by ``updateTimeline(concludingEnd:)``.
    private func watchFirstRender(serial: Int, deadline: TimeInterval) {
        guard case .realtime = renderingMode else { return }
        engineQueue.asyncAfter(deadline: .now() + .milliseconds(5)) { [weak self] in
            guard let self, serial == startTimingSerial, startTiming != nil else { return }
            noteFirstRender()
            guard startTiming != nil else { return }
            if StartupTiming.now() > deadline {
                startTiming?.renderTimedOut = true
                logStartTiming()
            } else {
                watchFirstRender(serial: serial, deadline: deadline)
            }
        }
    }

    private func noteFirstRender() {
        guard startTiming?.nodePlayedAt != nil, let sampleTime = nodeSampleTime(), sampleTime > 0 else { return }
        startTiming?.firstRenderedAt = StartupTiming.now()
        logStartTiming()
    }

    private func logStartTiming() {
        guard let timing = startTiming else { return }
        startTiming = nil
        lastStartTiming = timing
        let line = timing.logLine
        if timing.isSlow {
            engineLog.error("\(line, privacy: .public)")
        } else {
            engineLog.info("\(line, privacy: .public)")
        }
    }

    private func emitPosition() {
        guard let position else { return }
        let callback = callbackLock.withLock { callbacks.position }
        if let callback { callbackQueue.async { callback(position.uid, position.ms) } }
    }

    private func startTicker() {
        guard case .realtime = renderingMode, ticker == nil else { return }
        let timer = DispatchSource.makeTimerSource(queue: engineQueue)
        timer.schedule(deadline: .now() + .milliseconds(100), repeating: .milliseconds(100))
        timer.setEventHandler { [weak self] in
            self?.fill()
            self?.updateTimeline()
            self?.logBufferHealth()
        }
        timer.resume()
        ticker = timer
    }

    /// Seconds buffered ahead of the playhead while playing (#897): scheduled on the node, plus what the stream has
    /// fetched past the decoder. Logged every `healthLogSeconds`, and as it drops under `lowBufferSeconds`.
    private func logBufferHealth() {
        guard state == .playing, let current else { return }
        let scheduled = Double(max(0, outputIndex - playedStreamIndex())) / outputSampleRate
        let network = (current.source as? FFmpegTrackSource)?.networkBufferedSeconds
        let ahead = scheduled + (network ?? 0)
        let now = StartupTiming.now()
        let low = ahead < Self.lowBufferSeconds && network != nil && !drained
        let due = lastHealthLog.map { now - $0.at >= Self.healthLogSeconds } ?? true
        guard due || (low && lastHealthLog?.low == false) else { return }
        lastHealthLog = (now, low)
        let networkText = network.map { String(format: "%.1f", $0) } ?? "-"
        let line = "buffer: \(String(format: "%.1f", ahead)) s ahead (scheduled \(String(format: "%.1f", scheduled)) s, network \(networkText) s) uid \(current.track.uid)"
        if low { engineLog.warning("\(line, privacy: .public)") } else { engineLog.info("\(line, privacy: .public)") }
    }

    private func stopTicker() {
        ticker?.cancel()
        ticker = nil
    }

    /// The route or device changed and the engine stopped: rebuild from where the listener was. The
    /// node's clock stopped with the engine, so that is the last tick's reading (#714). An engine that
    /// won't start while the route settles is tried again (#715). Changes posted before the queue gets
    /// to them are one rebuild, the last's: each would re-seek the track, and a transcode's owner would
    /// re-open the stream for every one (#813).
    @objc private func engineConfigurationChanged(_ notification: Notification) {
        let change = configurationChangeLock.withLock {
            configurationChanges += 1
            return configurationChanges
        }
        engineQueue.async { [self] in
            guard change == configurationChangeLock.withLock({ configurationChanges }) else { return }
            guard current != nil else { return }
            let renderTime = player.lastRenderTime == nil ? "nil" : "valid"
            updateTimeline()
            let frame = currentMediaFrame()
            let snapshot = timelineLock.withLock { timeline }
            let held = snapshot.held.map(String.init) ?? "nil"
            engineLog.notice("""
                engine configuration changed: stream \(snapshot.playedStreamIndex(nodeTime: self.nodeSampleTime())), \
                heard \(snapshot.heard), held \(held, privacy: .public), render time \(renderTime, privacy: .public), \
                frame \(frame), playWhenReady \(self.playWhenReady), running \(self.engine.isRunning)
                """)
            restart(atFrame: frame, retryingStart: true)
        }
    }

    private func currentMediaFrame() -> Int64 {
        let stream = playedStreamIndex()
        return timelineLock.withLock {
            guard let segment = Self.segment(at: stream, in: timeline) else { return 0 }
            return segment.mediaStart + stream - segment.streamStart
        }
    }

    // MARK: - Timeline (any thread)

    private func nodeSampleTime() -> Int64? {
        guard let nodeTime = player.lastRenderTime, nodeTime.isSampleTimeValid,
              let playerTime = player.playerTime(forNodeTime: nodeTime)
        else { return nil }
        return playerTime.sampleTime
    }

    /// The stream index being heard.
    private func playedStreamIndex() -> Int64 {
        let snapshot = timelineLock.withLock { timeline }
        return snapshot.playedStreamIndex(nodeTime: nodeSampleTime())
    }

    private static func segment(at stream: Int64, in timeline: Timeline) -> Segment? {
        timeline.segments.last { $0.streamStart <= stream } ?? timeline.segments.first
    }
}
