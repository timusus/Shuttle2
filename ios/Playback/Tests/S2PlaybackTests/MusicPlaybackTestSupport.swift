import AVFoundation
import Foundation
@testable import S2Playback

/// A track held in memory as interleaved stereo float32, already at the controller's rate.
final class InMemoryTrackSource: TrackPCMSource {
    let samples: [Float]
    private var cursor = 0

    init(samples: [Float]) { self.samples = samples }

    var frameCount: Int { samples.count / 2 }

    func open(sampleRate: Double, channelCount: Int) throws -> Int64? {
        precondition(channelCount == 2)
        return Int64(frameCount)
    }

    func seek(toFrame frame: Int64) throws { cursor = min(Int(frame), frameCount) }

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
    private(set) var failures: [String] = []
    /// Frames rendered when each transition was seen, filled in by ``OfflineRenderer``.
    var transitionFrames: [Int] = []

    func attach(to controller: MusicPlaybackController) {
        controller.onTransition = { [weak self] in self?.transitions.append($0) }
        controller.onStateChanged = { [weak self] in self?.states.append($0) }
        controller.onFailed = { [weak self] uid, _ in self?.failures.append(uid) }
    }
}

/// Pulls a controller's output in small slices, pumping its scheduler between them the way the
/// position ticker does in real time.
struct OfflineRenderer {
    let controller: MusicPlaybackController
    let slice: AVAudioFrameCount

    /// `(left, right)` for the next `frames` frames.
    func render(frames: Int, log: CallbackLog? = nil, renderedBefore: Int = 0) throws -> (left: [Float], right: [Float]) {
        var left: [Float] = []
        var right: [Float] = []
        left.reserveCapacity(frames)
        right.reserveCapacity(frames)
        while left.count < frames {
            controller.pumpForTesting()
            let before = log?.transitions.count ?? 0
            let count = min(Int(slice), frames - left.count)
            let buffer = try controller.renderOffline(frameCount: AVAudioFrameCount(count))
            let data = buffer.floatChannelData!
            left.append(contentsOf: UnsafeBufferPointer(start: data[0], count: Int(buffer.frameLength)))
            right.append(contentsOf: UnsafeBufferPointer(start: data[1], count: Int(buffer.frameLength)))
            controller.pumpForTesting()
            if let log, log.transitions.count > before { log.transitionFrames.append(renderedBefore + left.count) }
        }
        return (left, right)
    }
}

extension Array where Element == Float {
    /// Channel `c` of interleaved stereo.
    func channel(_ c: Int) -> [Float] { stride(from: c, to: count, by: 2).map { self[$0] } }
}
