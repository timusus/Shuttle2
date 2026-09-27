import AVFoundation
import os

/// One queue item as the engine sees it: an id, its ReplayGain, and where its audio comes from.
///
/// The Kotlin side (`EnginePlayerController`, phase-6-playback.md) owns the queue, shuffle and
/// repeat; it hands the engine the current item and the next one and nothing else.
public struct PlaybackTrack {
    public let uid: String
    /// ReplayGain in dB, already resolved (track or album mode, preamp, clipping policy) by the
    /// shared Kotlin code. 0 is unity.
    public let gainDb: Float
    public let makeSource: () -> TrackPCMSource

    public init(uid: String, gainDb: Float = 0, makeSource: @escaping () -> TrackPCMSource) {
        self.uid = uid
        self.gainDb = gainDb
        self.makeSource = makeSource
    }

    /// A file or HTTP(S) URL decoded by FFmpeg.
    public init(uid: String, url: URL, headers: [String: String] = [:], gainDb: Float = 0) {
        self.init(uid: uid, gainDb: gainDb) { FFmpegTrackSource(url: url, headers: headers) }
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
/// **Threading.** Public methods may be called from any thread and return at once. Everything
/// that touches a source (open, seek, read: blocking I/O over HTTP) and every node operation runs
/// on one serial engine queue. A seek first interrupts a read stalled on the network so it is not
/// queued behind it. Callbacks arrive on `callbackQueue`.
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
    /// a listener can tell a late report for a track it has since replaced.
    public var onStateChanged: ((State, String?) -> Void)? {
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

    // MARK: Engine

    let engine = AVAudioEngine()
    private let player = AVAudioPlayerNode()
    private let timePitch = AVAudioUnitTimePitch()
    let format: AVAudioFormat
    private let renderingMode: RenderingMode
    private let engineQueue = DispatchQueue(label: "com.simplecityapps.shuttle2.playback.engine", qos: .userInitiated)
    private let callbackQueue: DispatchQueue
    private let log = Logger(subsystem: "com.simplecityapps.shuttle2", category: "MusicPlayback")

    /// How far ahead of the playhead audio is decoded and scheduled. Also how late an EQ change is
    /// heard, so it is short; the byte source's own read-ahead is what rides out the network.
    private let scheduleAheadFrames: Int64
    private static let chunkFrames = 4096

    // MARK: Engine-queue state

    private final class Slot {
        private static var lastId = 0
        /// Unique for the controller's life (engine queue only), unlike an object address.
        let id: Int
        let track: PlaybackTrack
        let source: TrackPCMSource
        var opened = false
        /// Opened and neither read nor sought since: the source is at its first frame.
        var atStart = true
        /// It could not be opened, or a read failed.
        var failed = false
        var durationFrames: Int64?

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
    private var state: State = .idle
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
    /// The queue's last frame is scheduled (current ended with no next).
    private var drained = false
    /// Bumped by every restart; completions from buffers a restart discarded are ignored.
    private var generation = 0
    private var scratch: [Float]
    private var ticker: DispatchSourceTimer?
    private var timePitchInGraph = false

    // MARK: Shared state (any thread, under `timelineLock`)

    private struct Segment {
        let streamStart: Int64
        let uid: String
        let mediaStart: Int64
        let durationFrames: Int64?
        let slot: Int
    }

    private struct Anchor {
        let stream: Int64
        let player: Int64
    }

    private struct Timeline {
        var segments: [Segment] = []
        var anchors: [Anchor] = [Anchor(stream: 0, player: 0)]
        /// Frames scheduled so far; the playhead never passes it.
        var scheduledEnd: Int64 = 0
        /// Non-nil while the node is not rendering (stopped, paused, not yet started).
        var held: Int64? = 0
        /// Where the node was released from `held`: the answer until its clock has rendered once.
        var resumedFrom: Int64 = 0
    }

    private let timelineLock = NSLock()
    private var timeline = Timeline()
    private let activeSourceLock = NSLock()
    private var activeSource: TrackPCMSource?

    private struct Callbacks {
        var state: ((State, String?) -> Void)?
        var transition: ((String) -> Void)?
        var failed: ((String, Error) -> Void)?
        var position: ((String, Int64) -> Void)?
    }

    private let callbackLock = NSLock()
    private var callbacks = Callbacks()

    public init(
        outputSampleRate: Double = 48_000,
        renderingMode: RenderingMode = .realtime,
        scheduleAheadSeconds: Double = 1.0,
        callbackQueue: DispatchQueue = .main
    ) throws {
        self.outputSampleRate = outputSampleRate
        self.renderingMode = renderingMode
        self.callbackQueue = callbackQueue
        self.scheduleAheadFrames = Int64(scheduleAheadSeconds * outputSampleRate)
        guard let format = AVAudioFormat(standardFormatWithSampleRate: outputSampleRate, channels: 2) else {
            throw TrackSourceError.failed("no stereo float format at \(outputSampleRate) Hz")
        }
        self.format = format
        processor = PCMProcessor(sampleRate: outputSampleRate, channelCount: 2)
        scratch = [Float](repeating: 0, count: Self.chunkFrames * 2)

        engine.attach(player)
        engine.attach(timePitch)
        connectGraph(speed: 1)

        if case let .offline(maximumFrameCount) = renderingMode {
            try engine.enableManualRenderingMode(.offline, format: format, maximumFrameCount: maximumFrameCount)
            try engine.start()
        } else {
            NotificationCenter.default.addObserver(
                self, selector: #selector(engineConfigurationChanged),
                name: .AVAudioEngineConfigurationChange, object: engine
            )
        }
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
        ticker?.cancel()
        engine.stop()
    }

    // MARK: - Public API

    /// Replace the queue with `current` (starting at `startMs`) and `next`.
    public func load(current track: PlaybackTrack, next nextTrack: PlaybackTrack?, startMs: Int64 = 0, playWhenReady: Bool) {
        interruptActiveRead()
        engineQueue.async { [self] in
            teardown()
            let slot = Slot(track: track)
            current = slot
            next = nextTrack.map(Slot.init)
            self.playWhenReady = playWhenReady
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
            if let old = next, old.opened {
                // The old next is already (partly or wholly) in the node's queue behind the
                // current track, and scheduled buffers cannot be taken back: rebuild.
                old.source.cancel()
                next = track.map(Slot.init)
                restart(atFrame: currentMediaFrame())
                return
            }
            next?.source.cancel()
            next = track.map(Slot.init)
            guard let next else { return }
            if state == .ended {
                if current.failed { promote(next, restartingAt: 0) }
            } else if drained, reading == nil {
                drained = false
                // The node may have run dry already: the new next starts where its clock is.
                if buffersInFlight == 0 { starved = true }
                beginReading(next)
                fill()
            }
        }
    }

    public func play() {
        engineQueue.async { [self] in
            playWhenReady = true
            guard current != nil, state != .ended else { return }
            startEngineIfNeeded()
            fill()
            player.play()
            releaseHold()
            setState(.playing)
            startTicker()
        }
    }

    public func pause() {
        engineQueue.async { [self] in
            playWhenReady = false
            let held = playedStreamIndex()
            player.pause()
            timelineLock.withLock { timeline.held = held }
            if current != nil, state == .playing || state == .loading { setState(.paused) }
            stopTicker()
            emitPosition()
        }
    }

    /// Seek within the current track. Exact: the first frame heard is the frame at `ms`.
    public func seek(toMs ms: Int64) {
        interruptActiveRead()
        engineQueue.async { [self] in
            guard current != nil else { return }
            updateTimeline()
            restart(atFrame: frames(ms: ms))
        }
    }

    public func stop() {
        interruptActiveRead()
        engineQueue.async { [self] in
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
    /// playhead (transitions, the end, position). Then waits for the callbacks it caused.
    func pumpForTesting() {
        engineQueue.sync {
            fill()
            updateTimeline()
        }
        callbackQueue.sync {}
    }

    /// Wait for everything already asked of the controller.
    func syncForTesting() {
        engineQueue.sync {}
        callbackQueue.sync {}
    }

    // MARK: - Engine queue

    private func frames(ms: Int64) -> Int64 { ms * Int64(outputSampleRate) / 1000 }

    private func ms(frames: Int64) -> Int64 { frames * 1000 / Int64(outputSampleRate) }

    private func setState(_ newState: State) {
        guard newState != state else { return }
        state = newState
        let uid = current?.track.uid
        let callback = callbackLock.withLock { callbacks.state }
        if let callback { callbackQueue.async { callback(newState, uid) } }
    }

    private func reportFailure(_ slot: Slot, _ error: Error) {
        slot.failed = true
        log.error("track \(slot.track.uid, privacy: .public) failed: \(String(describing: error), privacy: .public)")
        let uid = slot.track.uid
        let callback = callbackLock.withLock { callbacks.failed }
        if let callback { callbackQueue.async { callback(uid, error) } }
    }

    private func releaseHold() {
        timelineLock.withLock {
            timeline.resumedFrom = timeline.held ?? timeline.resumedFrom
            timeline.held = nil
        }
    }

    private func interruptActiveRead() {
        activeSourceLock.withLock { activeSource }?.interrupt()
    }

    private func setReading(_ slot: Slot?) {
        reading = slot
        activeSourceLock.withLock { activeSource = slot?.source }
    }

    private func startEngineIfNeeded() {
        guard case .realtime = renderingMode, !engine.isRunning else { return }
        do {
            try engine.start()
        } catch {
            log.error("engine start failed: \(String(describing: error), privacy: .public)")
        }
    }

    /// Opens `slot`'s source; false (after reporting it) if it could not be.
    @discardableResult
    private func openIfNeeded(_ slot: Slot) -> Bool {
        guard !slot.opened else { return true }
        do {
            slot.durationFrames = try slot.source.open(sampleRate: outputSampleRate, channelCount: outputChannelCount)
            slot.opened = true
            return true
        } catch {
            reportFailure(slot, error)
            return false
        }
    }

    /// Drop everything scheduled and start the stream again at `frame` of the current track.
    private func restart(atFrame frame: Int64) {
        guard let current else { return }
        generation += 1
        player.stop()
        // What the time-pitch unit already pulled belongs to the old stream.
        if timePitchInGraph { timePitch.reset() }
        buffersInFlight = 0
        starved = false
        drained = false
        inputIndex = 0
        outputIndex = 0
        if let pendingLimiter {
            processor.setLimiter(pendingLimiter)
            self.pendingLimiter = nil
        }
        processor.reset()
        // A next that had started to be read is re-opened from its start when it is reached again.
        if let old = next, old.opened {
            old.source.cancel()
            next = Slot(track: old.track)
        }
        timelineLock.withLock { timeline = Timeline() }
        setReading(current)
        var startFrame = frame
        // S2: a source still at its first frame is not sought to frame 0. The decoder's start trims
        // the encoder delay (an MP4's edit list, Opus pre-skip); FFmpeg's seek to the start of an
        // AAC-in-MP4 track does not, and ~2,100 frames of priming would open every load.
        if current.opened, !(frame == 0 && current.atStart) {
            current.atStart = false
            do {
                try current.source.seek(toFrame: frame)
            } catch {
                reportFailure(current, error)
                startFrame = 0
            }
        }
        appendSegment(for: current, mediaStart: startFrame)
        fill()
        if playWhenReady {
            startEngineIfNeeded()
            player.play()
            releaseHold()
            setState(.playing)
            startTicker()
        } else {
            setState(.paused)
        }
        emitPosition()
    }

    /// Carry on from the current track into `slot`, the next. A next that can't be opened gets no
    /// segment, so it is never transitioned into: the current track ends, and the owner, told of
    /// the failure, decides what follows it.
    private func beginReading(_ slot: Slot) {
        setReading(slot)
        if openIfNeeded(slot) { appendSegment(for: slot, mediaStart: 0) }
    }

    /// Make `slot` (the next) the current track, report the transition, and start the stream at
    /// `frame` of it.
    private func promote(_ slot: Slot, restartingAt frame: Int64) {
        current?.source.cancel()
        current = slot
        next = nil
        reportTransition(to: slot)
        openIfNeeded(slot)
        restart(atFrame: frame)
    }

    private func reportTransition(to slot: Slot) {
        let uid = slot.track.uid
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
        player.stop()
        stopTicker()
        current?.source.cancel()
        next?.source.cancel()
        current = nil
        next = nil
        setReading(nil)
        drained = false
        buffersInFlight = 0
        timelineLock.withLock { timeline = Timeline() }
    }

    /// Decode and schedule until `scheduleAheadFrames` are queued ahead of the playhead or the
    /// queue's end is scheduled.
    private func fill() {
        guard current != nil else { return }
        if let pendingEqualizer {
            processor.setEqualizer(pendingEqualizer)
            self.pendingEqualizer = nil
        }
        while !drained, outputIndex - playedStreamIndex() < scheduleAheadFrames {
            guard let buffer = readChunk() else { return }
            if buffer.frameLength > 0 { schedule(buffer) }
        }
    }

    /// Read up to one chunk from `reading`, crossing into `next` where the current track ends.
    /// nil when nothing could be read (an interrupted read, or nothing left).
    private func readChunk() -> AVAudioPCMBuffer? {
        var output: [Float] = []
        output.reserveCapacity(scratch.count)
        var filled = 0
        let channels = outputChannelCount
        var interrupted = false
        scratch.withUnsafeMutableBufferPointer { raw in
            let base = raw.baseAddress!
            while filled < Self.chunkFrames, let slot = reading {
                var got = 0
                if slot.opened {
                    slot.atStart = false
                    do {
                        got = try slot.source.read(into: base + filled * channels, maxFrames: Self.chunkFrames - filled)
                    } catch TrackSourceError.interrupted {
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
        if frameCount == 0 { return drained ? AVAudioPCMBuffer(pcmFormat: format, frameCapacity: 1) : nil }
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
        let generation = self.generation
        buffersInFlight += 1
        player.scheduleBuffer(buffer, at: nil, options: [], completionCallbackType: .dataConsumed) { [weak self] _ in
            self?.engineQueue.async {
                guard let self, self.generation == generation else { return }
                self.buffersInFlight -= 1
                if self.buffersInFlight == 0, !self.drained, self.state == .playing { self.starved = true }
                self.fill()
            }
        }
        outputIndex += Int64(buffer.frameLength)
        let end = outputIndex
        timelineLock.withLock { timeline.scheduledEnd = end }
    }

    /// Follow the playhead: promote the next track once it is being heard, notice the end.
    private func updateTimeline(concludingEnd: Bool = true) {
        guard current != nil else { return }
        let stream = playedStreamIndex()
        let segment = timelineLock.withLock { Self.segment(at: stream, in: timeline) }
        if let segment, let next, segment.slot == next.id {
            let old = current
            current = next
            self.next = nil
            if reading === old { setReading(next) }
            old?.source.cancel()
            timelineLock.withLock {
                timeline.segments.removeAll { $0.streamStart < segment.streamStart }
            }
            reportTransition(to: next)
        }
        if concludingEnd, drained, state == .playing, stream >= outputIndex {
            player.stop()
            timelineLock.withLock { timeline.held = outputIndex }
            stopTicker()
            setState(.ended)
        }
        if state == .playing { emitPosition() }
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
        }
        timer.resume()
        ticker = timer
    }

    private func stopTicker() {
        ticker?.cancel()
        ticker = nil
    }

    /// The route or device changed and the engine stopped: rebuild from where the listener was.
    @objc private func engineConfigurationChanged(_ notification: Notification) {
        engineQueue.async { [self] in
            guard current != nil else { return }
            updateTimeline()
            restart(atFrame: currentMediaFrame())
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
        if let held = snapshot.held { return held }
        guard let now = nodeSampleTime() else { return snapshot.resumedFrom }
        var stream: Int64 = 0
        for (i, anchor) in snapshot.anchors.enumerated() where anchor.player <= now {
            stream = anchor.stream + (now - anchor.player)
            // A starved node's clock ran on; the stream did not.
            if i + 1 < snapshot.anchors.count { stream = min(stream, snapshot.anchors[i + 1].stream) }
        }
        return max(0, min(stream, snapshot.scheduledEnd))
    }

    private static func segment(at stream: Int64, in timeline: Timeline) -> Segment? {
        timeline.segments.last { $0.streamStart <= stream } ?? timeline.segments.first
    }
}
