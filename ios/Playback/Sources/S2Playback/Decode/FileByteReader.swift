// Copied from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Spine/Sources/SpineNative/FileByteReader.swift — see ios/Playback/README.md.
import Foundation

/// A ``StreamByteReader`` over a local file: downloaded episodes, and every test in this package.
///
/// `pread` rather than `FileHandle` because the decoder's seek callback and its read callback are
/// the same conversation with the same descriptor — an explicit offset means there is no shared
/// file position to get out of step with ``position`` if the reader is ever driven from more than
/// one place.
public final class FileByteReader: StreamByteReader {

    private let descriptor: Int32
    private let length: Int64
    private let reportsLength: Bool
    private let lock = NSLock()
    private var offset: Int64 = 0
    private var cancelled = false
    private var interrupted = false
    private var closed = false

    /// - Parameter reportsTotalLength: when false, ``totalLength`` is nil even though the file has
    ///   one. This is not a curiosity: it is how the tests stand in for a chunked HTTP response
    ///   with no `Content-Length`, where `AVSEEK_SIZE` must answer "unknown" and MP3 has to work
    ///   anyway.
    public init(url: URL, reportsTotalLength: Bool = true) throws {
        let fd = url.withUnsafeFileSystemRepresentation { path -> Int32 in
            guard let path else { return -1 }
            return Foundation.open(path, O_RDONLY)
        }
        guard fd >= 0 else { throw StreamByteReaderError.transport("open failed: \(url.lastPathComponent)") }
        var info = stat()
        guard fstat(fd, &info) == 0 else {
            Foundation.close(fd)
            throw StreamByteReaderError.transport("fstat failed: \(url.lastPathComponent)")
        }
        descriptor = fd
        length = Int64(info.st_size)
        reportsLength = reportsTotalLength
    }

    deinit { if !closed { Foundation.close(descriptor) } }

    public var totalLength: Int64? {
        reportsLength ? length : nil
    }

    public var position: Int64 {
        lock.lock()
        defer { lock.unlock() }
        return offset
    }

    public func read(into buffer: UnsafeMutableRawPointer, maxLength: Int) throws -> Int {
        lock.lock()
        defer { lock.unlock() }
        if cancelled { throw StreamByteReaderError.cancelled }
        if interrupted { throw StreamByteReaderError.interrupted }
        guard maxLength > 0 else { return 0 }
        let n = pread(descriptor, buffer, maxLength, off_t(offset))
        if n < 0 { throw StreamByteReaderError.transport("pread failed at \(offset)") }
        offset += Int64(n)
        return n
    }

    public func seek(to newOffset: Int64) throws {
        lock.lock()
        defer { lock.unlock() }
        if cancelled { throw StreamByteReaderError.cancelled }
        if interrupted { throw StreamByteReaderError.interrupted }
        guard newOffset >= 0 else { throw StreamByteReaderError.unseekable }
        offset = newOffset
    }

    public func cancel() {
        lock.lock()
        defer { lock.unlock() }
        cancelled = true
    }

    /// A file read never blocks, so this only latches the refusal — which is still the contract the
    /// decoder relies on: an interrupted reader stays interrupted until the seek clears it.
    public func interrupt() {
        lock.lock()
        defer { lock.unlock() }
        interrupted = true
    }

    public func clearInterrupt() {
        lock.lock()
        defer { lock.unlock() }
        interrupted = false
    }
}
