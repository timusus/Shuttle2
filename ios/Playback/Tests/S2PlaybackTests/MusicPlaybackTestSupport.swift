import AVFoundation
import Foundation
@testable import S2Playback

/// A track held in memory as interleaved stereo float32, already at the controller's rate.
/// `seekable: false` is a progressive transcode: no length, and a seek fails the test.
final class InMemoryTrackSource: TrackPCMSource {
    let samples: [Float]
    let isSeekable: Bool
    private var cursor = 0

    init(samples: [Float], seekable: Bool = true) {
        self.samples = samples
        isSeekable = seekable
    }

    var frameCount: Int { samples.count / 2 }

    func open(sampleRate: Double, channelCount: Int) throws -> Int64? {
        precondition(channelCount == 2)
        return isSeekable ? Int64(frameCount) : nil
    }

    func seek(toFrame frame: Int64) throws {
        guard isSeekable else { throw TrackSourceError.failed("sought an unseekable source") }
        cursor = min(Int(frame), frameCount)
    }

    func read(into buffer: UnsafeMutablePointer<Float>, maxFrames: Int) throws -> Int {
        let frames = min(maxFrames, frameCount - cursor)
        guard frames > 0 else { return 0 }
        samples.withUnsafeBufferPointer { src in
            buffer.update(from: src.baseAddress! + cursor * 2, count: frames * 2)
        }
        cursor += frames
        return frames
    }

    func cancel() {}
    func interrupt() {}
}

/// A transcode whose length was estimated: seekable until the first seek, which it refuses, as
/// ``FFmpegTrackSource`` does when its reader can't serve the offset. Unseekable from then on, and
/// every read throws ``TrackSourceError/unseekable``.
final class RefusingTrackSource: TrackPCMSource {
    private let source: InMemoryTrackSource
    private let lock = NSLock()
    private var refused = false
    private(set) var seeks = 0

    init(samples: [Float]) {
        source = InMemoryTrackSource(samples: samples)
    }

    var isSeekable: Bool { lock.withLock { !refused } }

    func open(sampleRate: Double, channelCount: Int) throws -> Int64? {
        try source.open(sampleRate: sampleRate, channelCount: channelCount)
    }

    func seek(toFrame frame: Int64) throws {
        lock.withLock {
            seeks += 1
            refused = true
        }
        throw TrackSourceError.unseekable
    }

    func read(into buffer: UnsafeMutablePointer<Float>, maxFrames: Int) throws -> Int {
        if lock.withLock({ refused }) { throw TrackSourceError.unseekable }
        return try source.read(into: buffer, maxFrames: maxFrames)
    }

    func cancel() {}
    func interrupt() {}
}

/// A stream that stalls at `gateFrame`: reads there wait, saying so to the wait hook every 50 ms as a stalled
/// ``StreamedTrackReader`` does each second, until ``release()``. An interrupt ends the wait, and every read until a seek,
/// with ``TrackSourceError/interrupted``, as the real source's does.
final class StallingTrackSource: TrackPCMSource {
    private let inner: InMemoryTrackSource
    private let gateFrame: Int
    private let gate = DispatchSemaphore(value: 0)
    private let lock = NSLock()
    private var released = false
    private var interrupted = false
    private var delivered = 0
    private var waiting: (() -> Void)?

    init(samples: [Float], gateFrame: Int) {
        inner = InMemoryTrackSource(samples: samples)
        self.gateFrame = gateFrame
    }

    func release() { gate.signal() }

    func open(sampleRate: Double, channelCount: Int) throws -> Int64? {
        try inner.open(sampleRate: sampleRate, channelCount: channelCount)
    }

    func seek(toFrame frame: Int64) throws {
        try inner.seek(toFrame: frame)
        delivered = Int(frame)
        lock.withLock { interrupted = false }
    }

    func read(into buffer: UnsafeMutablePointer<Float>, maxFrames: Int) throws -> Int {
        if lock.withLock({ interrupted }) { throw TrackSourceError.interrupted }
        if delivered >= gateFrame, !lock.withLock({ released }) {
            while gate.wait(timeout: .now() + .milliseconds(50)) == .timedOut {
                if lock.withLock({ interrupted }) { throw TrackSourceError.interrupted }
                lock.withLock { waiting }?()
            }
            lock.withLock { released = true }
        }
        let frames = try inner.read(into: buffer, maxFrames: delivered < gateFrame ? min(maxFrames, gateFrame - delivered) : maxFrames)
        delivered += frames
        return frames
    }

    func cancel() { release() }
    func interrupt() { lock.withLock { interrupted = true } }

    func onReadWaiting(_ handler: @escaping () -> Void) {
        lock.withLock { waiting = handler }
    }
}

/// Passes another source through and keeps every frame it handed the controller, so a test can
/// compare the render with exactly what the decoder produced.
final class RecordingTrackSource: TrackPCMSource {
    private let inner: TrackPCMSource
    private(set) var recorded: [Float] = []

    init(_ inner: TrackPCMSource) { self.inner = inner }

    func open(sampleRate: Double, channelCount: Int) throws -> Int64? {
        try inner.open(sampleRate: sampleRate, channelCount: channelCount)
    }

    func seek(toFrame frame: Int64) throws {
        recorded.removeAll()
        try inner.seek(toFrame: frame)
    }

    func read(into buffer: UnsafeMutablePointer<Float>, maxFrames: Int) throws -> Int {
        let frames = try inner.read(into: buffer, maxFrames: maxFrames)
        recorded.append(contentsOf: UnsafeBufferPointer(start: buffer, count: frames * 2))
        return frames
    }

    func cancel() { inner.cancel() }
    func interrupt() { inner.interrupt() }
}

/// A track that can't be opened, as a missing file or a dead URL; or, `atRead`, one that opens and
/// fails on its first read, as a corrupt file.
final class FailingTrackSource: TrackPCMSource {
    private let atRead: Bool

    init(atRead: Bool = false) { self.atRead = atRead }

    func open(sampleRate: Double, channelCount: Int) throws -> Int64? {
        if atRead { return 48_000 }
        throw TrackSourceError.failed("can't open")
    }

    func seek(toFrame frame: Int64) throws {}

    func read(into buffer: UnsafeMutablePointer<Float>, maxFrames: Int) throws -> Int {
        throw TrackSourceError.failed("can't decode")
    }

    func cancel() {}
    func interrupt() {}
}

enum TestSignal {
    /// Deterministic noise in ±`amplitude`, never exactly zero, left and right different, so a
    /// sample out of place or a frame inserted is visible.
    static func noise(frames: Int, seed: UInt64, amplitude: Float = 0.5) -> [Float] {
        var state = seed &* 6364136223846793005 &+ 1442695040888963407
        var out = [Float](repeating: 0, count: frames * 2)
        for i in 0..<out.count {
            state = state &* 6364136223846793005 &+ 1442695040888963407
            let unit = Float(Double(state >> 11) / Double(1 << 53)) // [0, 1)
            var value = (unit * 2 - 1) * amplitude
            if value == 0 { value = amplitude / 3 }
            out[i] = value
        }
        return out
    }

    /// A 16-bit PCM WAV of a stereo sine, written to a temporary file.
    static func writeSineWAV(sampleRate: Int, seconds: Double, frequency: Double, amplitude: Double) throws -> URL {
        let frames = Int(Double(sampleRate) * seconds)
        var pcm = Data(capacity: frames * 4)
        for f in 0..<frames {
            let value = Int16((sin(2 * .pi * frequency * Double(f) / Double(sampleRate)) * amplitude * 32767).rounded())
            for _ in 0..<2 { withUnsafeBytes(of: value.littleEndian) { pcm.append(contentsOf: $0) } }
        }
        var wav = Data()
        func append<T: FixedWidthInteger>(_ v: T) { withUnsafeBytes(of: v.littleEndian) { wav.append(contentsOf: $0) } }
        wav.append(contentsOf: Array("RIFF".utf8)); append(UInt32(36 + pcm.count))
        wav.append(contentsOf: Array("WAVE".utf8))
        wav.append(contentsOf: Array("fmt ".utf8)); append(UInt32(16)); append(UInt16(1)); append(UInt16(2))
        append(UInt32(sampleRate)); append(UInt32(sampleRate * 4)); append(UInt16(4)); append(UInt16(16))
        wav.append(contentsOf: Array("data".utf8)); append(UInt32(pcm.count))
        wav.append(pcm)
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("s2-\(sampleRate)-\(UUID().uuidString).wav")
        try wav.write(to: url)
        return url
    }
}

/// Collects the controller's callbacks from its callback queue.
final class CallbackLog {
    let queue = DispatchQueue(label: "test.callbacks")
    private(set) var transitions: [String] = []
    private(set) var states: [MusicPlaybackController.State] = []
    /// ``states``, read from any thread.
    var statesSoFar: [MusicPlaybackController.State] { queue.sync { states } }
    /// The commands taken before each of `states`.
    private(set) var stateCommands: [Int] = []
    private(set) var failures: [String] = []
    private(set) var seeksUnsupported: [String] = []
    /// Frames rendered when each transition was seen, filled in by ``OfflineRenderer``.
    var transitionFrames: [Int] = []

    func attach(to controller: MusicPlaybackController) {
        controller.onTransition = { [weak self] in self?.transitions.append($0) }
        controller.onStateChanged = { [weak self] state, _, commands in
            self?.states.append(state)
            self?.stateCommands.append(commands)
        }
        controller.onFailed = { [weak self] uid, _ in self?.failures.append(uid) }
        controller.onSeekUnsupported = { [weak self] uid, ms in self?.seeksUnsupported.append("\(uid) \(ms)") }
    }
}

/// Pulls a controller's output in small slices, pumping its scheduler between them the way the
/// position ticker does in real time.
struct OfflineRenderer {
    let controller: MusicPlaybackController
    let slice: AVAudioFrameCount
    /// Wait out the next track's open at each pump, as if it were instant. False renders what a
    /// listener hears while it's slow.
    var awaitingOpens = true

    /// `(left, right)` for the next `frames` frames.
    func render(frames: Int, log: CallbackLog? = nil, renderedBefore: Int = 0) throws -> (left: [Float], right: [Float]) {
        var left: [Float] = []
        var right: [Float] = []
        left.reserveCapacity(frames)
        right.reserveCapacity(frames)
        while left.count < frames {
            controller.pumpForTesting(awaitingOpens: awaitingOpens)
            let before = log?.transitions.count ?? 0
            let count = min(Int(slice), frames - left.count)
            let buffer = try controller.renderOffline(frameCount: AVAudioFrameCount(count))
            let data = buffer.floatChannelData!
            left.append(contentsOf: UnsafeBufferPointer(start: data[0], count: Int(buffer.frameLength)))
            right.append(contentsOf: UnsafeBufferPointer(start: data[1], count: Int(buffer.frameLength)))
            controller.pumpForTesting(awaitingOpens: awaitingOpens)
            if let log, log.transitions.count > before { log.transitionFrames.append(renderedBefore + left.count) }
        }
        return (left, right)
    }
}

extension Array where Element == Float {
    /// Channel `c` of interleaved stereo.
    func channel(_ c: Int) -> [Float] { stride(from: c, to: count, by: 2).map { self[$0] } }
}
