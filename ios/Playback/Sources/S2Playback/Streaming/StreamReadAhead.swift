import PlaybackStreaming

/// How far a stream may download ahead of the decoder while the network is expensive or constrained (cellular, a
/// hotspot, Low Data Mode): a minute of audio. On Wi-Fi the whole file downloads, as before (shuttle-playback ADR-0013).
enum StreamReadAhead {
    static let seconds: Int64 = 60
    /// The rate assumed when nothing says what the stream's is: the highest a lossy stream usually has.
    static let fallbackKbps: Int64 = 320

    /// ``seconds`` of audio at the library's bitrate when it knows it, else at the file's average (`sizeBytes` over
    /// `durationMs`), else at ``fallbackKbps``. A transcode is sized by its source's rate, an over-estimate.
    static func readAhead(bitrateKbps: Int?, sizeBytes: Int64?, durationMs: Int64?) -> GrowingFileReadAhead {
        let bitsPerSecond: Int64
        if let bitrateKbps, bitrateKbps > 0 {
            bitsPerSecond = Int64(bitrateKbps) * 1000
        } else if let sizeBytes, let durationMs, sizeBytes > 0, durationMs > 0 {
            bitsPerSecond = sizeBytes * 8 * 1000 / durationMs
        } else {
            bitsPerSecond = fallbackKbps * 1000
        }
        return GrowingFileReadAhead(bytes: max(1, bitsPerSecond * seconds / 8))
    }
}
