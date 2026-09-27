// Copied from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Spine/Sources/SpineNative/StreamByteReader.swift — see ios/Playback/README.md.
import Foundation

/// **Blocking, seekable byte access for the streaming decoder.**
///
/// The playback decoder drives FFmpeg through a custom `AVIOContext` whose read and seek
/// callbacks land here (plan: `mobile/ios/docs/plans/2026-09-09-streaming-audio-pipeline.md`, §1
/// and §5). Two implementations exist: a file-backed reader for downloaded episodes and an HTTP
/// range reader in the app that fetches, tees the raw bytes to the ad-skip spine, and discards.
/// Nothing above this protocol knows which one it has.
///
/// Every call runs on the decoder's own thread and may block: `read` waits for bytes to arrive,
/// `seek` may cancel and reopen a transport. `cancel()` is the one call made from another thread;
/// it must unblock a waiting `read` or `seek`, which then throws ``StreamByteReaderError/cancelled``.
public protocol StreamByteReader: AnyObject {

    /// Total length in bytes, or nil while unknown. FFmpeg asks for it (`AVSEEK_SIZE`) to reach a
    /// trailing `moov` with one seek instead of read-discarding the whole `mdat`.
    var totalLength: Int64? { get }

    /// Absolute offset the next `read` returns bytes from.
    var position: Int64 { get }

    /// Copy up to `maxLength` bytes at ``position`` into `buffer`, advance ``position`` by the
    /// count, and return it. Blocks until at least one byte is available. Returns 0 only at end of
    /// stream. Throws on cancellation or a transport failure it could not recover from.
    func read(into buffer: UnsafeMutableRawPointer, maxLength: Int) throws -> Int

    /// Move ``position`` to `offset`. Throws ``StreamByteReaderError/unseekable`` when the source
    /// cannot serve that offset, or `cancelled`.
    func seek(to offset: Int64) throws

    /// Abort any blocked or future call. Idempotent; safe from any thread.
    func cancel()

    /// Bring the blocked call back so the caller can seek — **without ending the stream**.
    ///
    /// A player whose pull loop is waiting on a stalled connection cannot apply a seek until the
    /// read returns, and on a dead link it never does. Cancelling would answer the seek and destroy
    /// the reader; this leaves it usable, and ``clearInterrupt()`` (which the decoder calls as part
    /// of its next seek) puts it back to work. Idempotent; safe from any thread.
    func interrupt()

    /// Forget an interruption. Called by the decoder before the seek that follows one.
    func clearInterrupt()
}

public enum StreamByteReaderError: Error, Equatable {
    case cancelled
    /// The call was ended early by ``StreamByteReader/interrupt()``. Recoverable: unlike
    /// `cancelled` the reader may be read from again after ``StreamByteReader/clearInterrupt()``.
    case interrupted
    case unseekable
    case transport(String)
}
