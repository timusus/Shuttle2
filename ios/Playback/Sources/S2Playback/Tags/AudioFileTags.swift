import CS2StreamDecode
import Foundation

/// A local audio file's tags and audio properties (#590), read with the FFmpeg build the engine plays with
/// (`tag_read.c`), so the library reads tags from every format it can play: ID3, MP4 atoms, Vorbis comments
/// (FLAC, Ogg Vorbis, Opus) and Matroska tags alike. AVFoundation reads none of FLAC's or Ogg's, and none of
/// the ReplayGain or MusicBrainz tags in any format.
///
/// The mapping follows Android's (`AudioFileExt.kt`'s `toFileTags`): ARTIST is split on ';' into `artists`
/// and kept whole as `artistDisplay`; GENRE is split on ',', ';' and '/'; TRACK and DISC read "3/12"; the
/// year comes from ORIGINALDATE (so a remaster or reissue keeps the album's original year), then DATE, then YEAR. A missing tag stays nil or empty, with no fallback.
public struct AudioFileTags: Equatable, Sendable {
    public var title: String?
    public var artists: [String] = []
    public var artistDisplay: String?
    /// The ARTISTS multi-value tag, as written.
    public var artistsTag: [String] = []
    public var albumArtist: String?
    /// The ALBUMARTISTS multi-value tag, as written.
    public var albumArtists: [String] = []
    public var album: String?
    public var track: Int?
    public var trackTotal: Int?
    public var disc: Int?
    public var discTotal: Int?
    public var year: Int?
    public var genres: [String] = []
    public var replayGainTrack: Double?
    public var replayGainAlbum: Double?
    public var lyrics: String?
    public var grouping: String?
    public var compilation: Bool?
    public var mbTrackId: String?
    public var mbAlbumId: String?
    public var mbReleaseGroupId: String?
    public var mbArtistIds: [String] = []
    public var mbAlbumArtistIds: [String] = []

    public var durationMs: Int64?
    public var sampleRate: Int?
    public var channelCount: Int?
    public var bitDepth: Int?
    /// Kilobits per second, as Android's TagLib reports it.
    public var bitRateKbps: Int?
    /// libavcodec's name for the codec ("flac", "alac", "mp3").
    public var codec: String?

    public init() {}

    /// Maps tags as libavformat names them (any case and spelling: "album_artist", "ALBUMARTIST", "MusicBrainz
    /// Album Id") to their fields. A key read twice keeps its first value: the container's over the stream's.
    public init(tags: [(key: String, value: String)]) {
        var byKey: [String: String] = [:]
        for (key, value) in tags {
            let normalized = Self.normalize(key)
            // ID3's USLT arrives as "lyrics-eng", one key per language.
            let name = normalized.hasPrefix("lyrics") ? "lyrics" : normalized
            if byKey[name] == nil { byKey[name] = value }
        }
        func first(_ names: String...) -> String? {
            names.lazy.compactMap { byKey[$0]?.trimmingCharacters(in: .whitespacesAndNewlines) }.first { !$0.isEmpty }
        }

        title = first("title")
        let artistTag = first("artist")
        artists = artistTag.map { Self.split($0, on: [";"]) } ?? []
        artistDisplay = artistTag
        artistsTag = first("artists").map { Self.split($0, on: [";"]) } ?? []
        albumArtist = first("albumartist")
        albumArtists = first("albumartists").map { Self.split($0, on: [";"]) } ?? []
        album = first("album")
        (track, trackTotal) = Self.numberAndTotal(first("track", "tracknumber"))
        (disc, discTotal) = Self.numberAndTotal(first("disc", "discnumber"))
        // The original release's year beats a reissue's: ffmpeg names ID3's TDOR/TORY raw, and Vorbis/MP4's ORIGINALDATE as is.
        year = [first("originaldate", "tdor", "tory"), first("date"), first("year")].lazy.compactMap { $0.flatMap(Self.year) }.first
        genres = first("genre").map { Self.split($0, on: [",", ";", "/"]) } ?? []
        replayGainTrack = first("replaygaintrackgain").flatMap(Self.decibels)
        replayGainAlbum = first("replaygainalbumgain").flatMap(Self.decibels)
        lyrics = first("lyrics", "unsyncedlyrics")
        grouping = first("grouping", "contentgroup")
        compilation = first("compilation").map { ["1", "true", "yes"].contains($0.lowercased()) }
        mbTrackId = first("musicbrainztrackid").map { Self.split($0, on: [";", "/"]) }?.first
        mbAlbumId = first("musicbrainzalbumid").map { Self.split($0, on: [";", "/"]) }?.first
        mbReleaseGroupId = first("musicbrainzreleasegroupid").map { Self.split($0, on: [";", "/"]) }?.first
        mbArtistIds = first("musicbrainzartistid").map { Self.split($0, on: [";", "/"]) } ?? []
        mbAlbumArtistIds = first("musicbrainzalbumartistid").map { Self.split($0, on: [";", "/"]) } ?? []
    }

    /// `url`'s tags and properties, or nil when it can't be opened or holds no audio this build can demux.
    public static func read(fileAt url: URL) -> AudioFileTags? {
        final class Collected { var tags: [(key: String, value: String)] = [] }
        let collected = Collected()
        var properties = S2AudioProperties()
        let status = url.withUnsafeFileSystemRepresentation { path -> Int32 in
            guard let path else { return -1 }
            return s2_read_tags(path, Unmanaged.passUnretained(collected).toOpaque(), { context, key, value in
                guard let context, let key, let value else { return }
                let collected = Unmanaged<Collected>.fromOpaque(context).takeUnretainedValue()
                collected.tags.append((String(cString: key), String(cString: value)))
            }, &properties)
        }
        guard status == 0 else { return nil }

        var tags = AudioFileTags(tags: collected.tags)
        tags.durationMs = properties.duration_ms > 0 ? properties.duration_ms : nil
        tags.sampleRate = properties.sample_rate > 0 ? Int(properties.sample_rate) : nil
        tags.channelCount = properties.channels > 0 ? Int(properties.channels) : nil
        tags.bitDepth = properties.bit_depth > 0 ? Int(properties.bit_depth) : nil
        tags.bitRateKbps = properties.bit_rate > 0 ? Int(properties.bit_rate / 1000) : nil
        let codec = withUnsafeBytes(of: properties.codec) { bytes in
            String(decoding: bytes.prefix { $0 != 0 }, as: UTF8.self)
        }
        tags.codec = codec.isEmpty ? nil : codec
        return tags
    }

    /// The encoded image embedded in `url` (a front cover, usually), or nil when it has none.
    public static func embeddedPicture(fileAt url: URL) -> Data? {
        url.withUnsafeFileSystemRepresentation { path -> Data? in
            guard let path else { return nil }
            var bytes: UnsafeMutablePointer<UInt8>?
            var size = 0
            guard s2_read_picture(path, &bytes, &size) == 0, let bytes else { return nil }
            defer { s2_free_picture(bytes) }
            return Data(bytes: bytes, count: size)
        }
    }

    // MARK: - Parsing

    /// Lowercased with everything but letters and digits dropped, so "MusicBrainz Album Id",
    /// "MUSICBRAINZ_ALBUMID" and "musicbrainz_albumid" are one key.
    static func normalize(_ key: String) -> String {
        String(key.lowercased().unicodeScalars.filter { CharacterSet.alphanumerics.contains($0) }.map(Character.init))
    }

    static func split(_ value: String, on separators: Set<Character>) -> [String] {
        value.split(whereSeparator: { separators.contains($0) })
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }

    /// "3/12" as (3, 12), "3" as (3, nil).
    static func numberAndTotal(_ value: String?) -> (Int?, Int?) {
        guard let value else { return (nil, nil) }
        let parts = value.split(separator: "/", maxSplits: 1).map { $0.trimmingCharacters(in: .whitespaces) }
        return (parts.first.flatMap { Int($0) }, parts.count > 1 ? Int(parts[1]) : nil)
    }

    /// The year a date tag starts with: "1997", "1997-05-21", "1997-05-21T10:00:00".
    static func year(_ value: String) -> Int? {
        let digits = value.prefix { $0.isNumber }
        guard digits.count == 4, let year = Int(digits), year > 0 else { return nil }
        return year
    }

    /// "-6.48 dB" as -6.48.
    static func decibels(_ value: String) -> Double? {
        let number = value.lowercased().replacingOccurrences(of: "db", with: "").trimmingCharacters(in: .whitespaces)
        return Double(number)
    }
}
