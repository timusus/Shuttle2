import Foundation

/// One queue item as the engine sees it: an id, its ReplayGain, and where its audio comes from.
///
/// The Kotlin side (`EnginePlayerController`) owns the queue, shuffle and
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

    /// A file or HTTP(S) URL decoded by FFmpeg. A stream's `bitrateKbps` and `sizeBytes` (the library's) size how far
    /// it downloads ahead on an expensive network path (``StreamReadAhead``).
    public init(
        uid: String,
        url: URL,
        headers: [String: String] = [:],
        gainDb: Float = 0,
        expectedDurationMs: Int64? = nil,
        bitrateKbps: Int? = nil,
        sizeBytes: Int64? = nil
    ) {
        let identity = ([url.absoluteString] + headers.sorted { $0.key < $1.key }.map { "\($0.key): \($0.value)" })
            .joined(separator: "\n")
        let readAhead = url.isFileURL
            ? nil
            : StreamReadAhead.readAhead(bitrateKbps: bitrateKbps, sizeBytes: sizeBytes, durationMs: expectedDurationMs)
        self.init(uid: uid, streamIdentity: identity, gainDb: gainDb, expectedDurationMs: expectedDurationMs) {
            FFmpegTrackSource(url: url, headers: headers, readAhead: readAhead)
        }
    }

    /// Whether `other` is a hand-over of the same stream.
    func playsSameStream(as other: PlaybackTrack) -> Bool {
        streamIdentity != nil && streamIdentity == other.streamIdentity
    }
}
