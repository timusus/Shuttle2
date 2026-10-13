import Foundation
import PlaybackDecode
import PlaybackRender

/// A stream's bytes that may be a progressive transcode, which a server starts at a time offset rather than a byte range.
public protocol TimeOffsetByteReader: StreamByteReader {
    /// Once the stream has been read from: the whole item's duration in ms if it is a transcode the server starts at a
    /// time offset; nil for a file or a stream served as stored.
    var transcodeDurationMs: Int64? { get }
}

/// An item played through `FFmpegItemSource` that, when its stream turns out to be a progressive transcode, seeks by
/// re-opening the stream at a time offset: a transcode has no byte length to seek in. It declares the song's duration
/// and is seekable, and a transcode that fails or ends more than 5 s short of that duration (a dropped connection reads
/// as a clean end) is re-opened at its position the same way. Files and streams served as stored seek as the inner
/// source does.
///
/// Re-opens ask for whole seconds, the one precision every server honours (Subsonic's offsets are whole seconds), and
/// drop the frames up to the target, so a seek lands where it was asked. Three re-opens in a row that play less than a
/// second end the item (or fail it, after an error), so a server that always truncates can't loop.
///
/// `@unchecked Sendable`: the inner source, epoch and interrupt are guarded by `lock`; the rest is the executor's.
public final class TimeOffsetItemSource: ItemSource, @unchecked Sendable {
    /// The item's stream started `startMs` into the song. Called on the executor; the reader may resolve lazily.
    public typealias Open = (_ startMs: Int64) -> TimeOffsetByteReader

    private static let shortEndSeconds = 5.0
    private static let reopenBudget = 3

    private let openReader: Open
    private let lock = NSLock()
    private var inner: FFmpegItemSource?
    private var epoch: UInt64 = 0
    private var interruptedThrough: UInt64?

    private var format: PCMFormat?
    private var isTranscode = false
    public private(set) var frameCount: Int64?
    public private(set) var isSeekable = false
    /// The media frame the next chunk starts at, and the frames still to drop before it after a re-open.
    private var position: Int64 = 0
    private var framesToDrop: Int64 = 0
    private var framesSinceReopen: Int64 = 0
    private var reopensLeft = reopenBudget
    private var ended = false

    public init(open: @escaping Open) {
        openReader = open
    }

    public func begin(epoch: UInt64) {
        lock.withLock {
            self.epoch = epoch
            inner?.begin(epoch: epoch)
        }
    }

    public func open() throws -> PCMFormat {
        let reader = openReader(0)
        let source = attach(reader)
        let format = try source.open()
        self.format = format
        if let durationMs = reader.transcodeDurationMs, durationMs > 0 {
            isTranscode = true
            frameCount = frames(seconds: Double(durationMs) / 1000)
            isSeekable = true
        } else {
            frameCount = source.frameCount
            isSeekable = source.isSeekable
        }
        return format
    }

    public func seek(toFrame frame: Int64) throws -> Int64 {
        guard isTranscode else { return try current().seek(toFrame: frame) }
        reopensLeft = Self.reopenBudget
        if let frameCount, frame >= frameCount {
            position = frame
            ended = true
            return frame
        }
        try reopen(at: frame)
        return frame
    }

    public func nextChunk() throws -> [Float]? {
        guard isTranscode else { return try current().nextChunk() }
        while !ended {
            let chunk: [Float]?
            do {
                chunk = try current().nextChunk()
            } catch {
                if isInterrupted { throw error }
                try reopenAtPosition(after: error)
                continue
            }
            guard var chunk else {
                if let frameCount, position < frameCount - frames(seconds: Self.shortEndSeconds) {
                    try reopenAtPosition(after: nil)
                    continue
                }
                return nil
            }
            let channels = Int(format?.channelCount ?? 1)
            if framesToDrop > 0 {
                let dropped = min(framesToDrop, Int64(chunk.count / channels))
                framesToDrop -= dropped
                chunk.removeFirst(Int(dropped) * channels)
                if chunk.isEmpty { continue }
            }
            let count = Int64(chunk.count / channels)
            position += count
            framesSinceReopen += count
            if framesSinceReopen >= frames(seconds: 1) { reopensLeft = Self.reopenBudget }
            return chunk
        }
        return nil
    }

    public func interrupt(through epoch: UInt64) {
        lock.withLock {
            interruptedThrough = max(interruptedThrough ?? epoch, epoch)
            inner?.interrupt(through: epoch)
        }
    }

    public func release() {
        lock.withLock { () -> FFmpegItemSource? in
            defer { inner = nil }
            return inner
        }?.release()
    }

    /// Re-opens at `position` after a failure or a short end, within the budget; past it the error is thrown, or the
    /// item ends.
    private func reopenAtPosition(after error: Error?) throws {
        var error = error
        while reopensLeft > 0 {
            reopensLeft -= 1
            do {
                try reopen(at: position)
                return
            } catch let failure {
                if isInterrupted { throw failure }
                error = failure
            }
        }
        if let error { throw error }
        ended = true
    }

    private func reopen(at frame: Int64) throws {
        guard let format else { throw StreamDecoderError.invalidState("not open") }
        let startSeconds = (Double(frame) / format.sampleRate).rounded(.down)
        let reopened = try attach(openReader(Int64(startSeconds) * 1000)).open()
        guard reopened.sampleRate == format.sampleRate, reopened.channelCount == format.channelCount else {
            throw StreamDecoderError.invalidState("the transcode re-opened in another format")
        }
        position = frame
        framesToDrop = max(0, frame - frames(seconds: startSeconds))
        framesSinceReopen = 0
        ended = false
    }

    /// Makes `reader`'s source the current one, with this epoch and any interrupt aimed at it, and releases the last.
    private func attach(_ reader: StreamByteReader) -> FFmpegItemSource {
        let source = FFmpegItemSource(reader: reader)
        let replaced = lock.withLock { () -> FFmpegItemSource? in
            source.begin(epoch: epoch)
            if let interruptedThrough { source.interrupt(through: interruptedThrough) }
            defer { inner = source }
            return inner
        }
        replaced?.release()
        return source
    }

    private func current() throws -> FFmpegItemSource {
        guard let inner = lock.withLock({ inner }) else { throw StreamDecoderError.invalidState("not open") }
        return inner
    }

    private var isInterrupted: Bool {
        lock.withLock { interruptedThrough.map { $0 >= epoch } ?? false }
    }

    private func frames(seconds: Double) -> Int64 {
        Int64((seconds * (format?.sampleRate ?? 0)).rounded())
    }
}
