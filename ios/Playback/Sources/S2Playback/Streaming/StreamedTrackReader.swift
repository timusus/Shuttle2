import Foundation
import PlaybackDecode
import PlaybackStreaming

/// A ``GrowingFileByteSource`` with what the engine needs and the source lacks: `onWait` every ``waitSeconds`` a read
/// waits, and a transcode's restart from byte 0 given up as ``dropped``
/// so the owner re-opens it at the position instead of waiting for the song to download again.
final class StreamedTrackReader: StreamByteReader {
    static let waitSeconds: Double = 1

    let source: GrowingFileByteSource
    private let onWait: () -> Void
    private let queue = DispatchQueue(label: "com.simplecityapps.shuttle2.stream-read", qos: .userInitiated)
    private let watch: DropWatch

    /// `makeSource` builds the source with the event handler it's given, which the drop watch needs.
    init(onWait: @escaping () -> Void, makeSource: (_ onEvent: @escaping (GrowingFileEvent) -> Void) -> GrowingFileByteSource) {
        let watch = DropWatch()
        source = makeSource { [weak watch] event in watch?.observe(event) }
        watch.source = source
        self.watch = watch
        self.onWait = onWait
    }

    /// The download restarted from byte 0 past the start, and was given up.
    var dropped: Bool { watch.dropped }

    /// From now on a restart from byte 0 is a drop. Not before: a retry while the decoder probes is cheap, and an open
    /// that ended interrupted would fail the track.
    func armDropWatch() { watch.arm() }

    var totalLength: Int64? { source.totalLength }

    var position: Int64 { source.position }

    func read(into buffer: UnsafeMutableRawPointer, maxLength: Int) throws -> Int {
        if dropped { throw StreamByteReaderError.interrupted }
        // Upstream need: a hook on GrowingFileByteSource called while a read waits, so this helper queue can go.
        let done = DispatchSemaphore(value: 0)
        var result: Result<Int, Error> = .success(0)
        queue.async { [source] in
            result = Result { try source.read(into: buffer, maxLength: maxLength) }
            done.signal()
        }
        while done.wait(timeout: .now() + Self.waitSeconds) == .timedOut {
            onWait()
        }
        // The cancel that gave the download up wakes the read with a cancel, which would end the decode as cancelled.
        if dropped { throw StreamByteReaderError.interrupted }
        return try result.get()
    }

    func seek(to offset: Int64) throws {
        if dropped { throw StreamByteReaderError.interrupted }
        try source.seek(to: offset)
    }

    func cancel() { source.cancel() }

    func interrupt() { source.interrupt() }

    func clearInterrupt() { source.clearInterrupt() }
}

/// Watches a source's transactions for a `200` from byte 0 with the read past it: the source only re-declares a file as
/// starting at byte 0 when a host ignored its range, while a host that honours ranges answers every restart, even
/// one from byte 0, with a `206`.
/// Upstream need: GrowingFileByteSource reporting a resume its host refused, so this needn't infer it.
private final class DropWatch {
    weak var source: GrowingFileByteSource?
    private let lock = NSLock()
    private var armed = false
    private var generation: Int?
    private var isDropped = false

    var dropped: Bool { lock.withLock { isDropped } }

    func arm() { lock.withLock { armed = true } }

    /// On the session's delegate queue or the decoder's thread, never under the source's lock.
    func observe(_ event: GrowingFileEvent) {
        guard case .transaction(let base, let generation, _, let status) = event, let source else { return }
        let restarted: Bool = lock.withLock {
            defer { self.generation = generation }
            guard armed, !isDropped, let last = self.generation, generation != last, base == 0, status == 200 else {
                return false
            }
            guard source.position > 0 else { return false }
            isDropped = true
            return true
        }
        if restarted { source.cancel() }
    }
}
