import Foundation
import PlaybackDecode
import PlaybackStreaming

/// A stream's ``GrowingFileByteSource`` as the decoder reads it, with the two things the engine needs that the source
/// doesn't give:
///
/// - **A read that waits reports it** (#897): `onWait` is called on the reading thread after every ``waitSeconds`` a
///   read has waited for its bytes, so the engine can tell a stall from inside its own read. The source parks a read
///   with no hook, so each read runs on a helper queue while the reading thread waits for it.
/// - **A seek past the stream's length is refused** (#950): a transcode's estimated length can promise bytes the
///   server never sends. The decoder reports the refusal as unseekable, which the engine reports as a seek it can't
///   make (`onSeekUnsupported`), never as a failure.
final class StreamedTrackReader: StreamByteReader {
    static let waitSeconds: Double = 1

    let source: GrowingFileByteSource
    private let onWait: () -> Void
    private let queue = DispatchQueue(label: "com.simplecityapps.shuttle2.stream-read")

    init(_ source: GrowingFileByteSource, onWait: @escaping () -> Void) {
        self.source = source
        self.onWait = onWait
    }

    var totalLength: Int64? { source.totalLength }

    var position: Int64 { source.position }

    func read(into buffer: UnsafeMutableRawPointer, maxLength: Int) throws -> Int {
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
        return try result.get()
    }

    func seek(to offset: Int64) throws {
        // Upstream need: GrowingFileByteSource refusing a seek past its length as `unseekable` itself.
        if let total = source.totalLength, offset > total { throw StreamByteReaderError.unseekable }
        try source.seek(to: offset)
    }

    func cancel() { source.cancel() }

    func interrupt() { source.interrupt() }

    func clearInterrupt() { source.clearInterrupt() }
}
