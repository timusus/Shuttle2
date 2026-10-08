import CS2Tags
import Foundation

/// A local audio file's tags and audio properties (#590), read with the FFmpeg build the engine plays with
/// (`tag_read.c`), so the library reads tags from every format it can play: ID3, MP4 atoms, Vorbis comments
/// (FLAC, Ogg Vorbis, Opus) and Matroska tags alike. AVFoundation reads none of FLAC's or Ogg's, and none of
/// the ReplayGain or MusicBrainz tags in any format.
///
/// The tags are libavformat's, raw: the shared Kotlin library maps them to a song's fields with the rules
/// Android's TagLib reader uses (`ffmpegPropertyMap`, then `toFileTags`, in `:android:mediaprovider:local`, #810).
public struct AudioFileTags: Equatable, Sendable {
    /// One tag as libavformat names it ("album_artist", "TDOR", "MusicBrainz Album Id"), with its value.
    public struct Tag: Equatable, Sendable {
        public var key: String
        public var value: String

        public init(key: String, value: String) {
            self.key = key
            self.value = value
        }
    }

    /// In reading order: the container's tags, then the audio stream's.
    public var tags: [Tag] = []

    public var durationMs: Int64?
    public var sampleRate: Int?
    public var channelCount: Int?
    public var bitDepth: Int?
    /// Kilobits per second, as Android's TagLib reports it.
    public var bitRateKbps: Int?
    /// libavcodec's name for the codec ("flac", "alac", "mp3").
    public var codec: String?

    public init() {}

    /// The first value of the tag named `key` as libavformat spells it, or nil when it isn't tagged.
    func value(_ key: String) -> String? {
        tags.first { $0.key == key }?.value
    }

    /// `url`'s tags and properties, or nil when it can't be opened or holds no audio this build can demux.
    public static func read(fileAt url: URL) -> AudioFileTags? {
        final class Collected { var tags: [Tag] = [] }
        let collected = Collected()
        var properties = S2AudioProperties()
        let status = url.withUnsafeFileSystemRepresentation { path -> Int32 in
            guard let path else { return -1 }
            return s2_read_tags(path, Unmanaged.passUnretained(collected).toOpaque(), { context, key, value in
                guard let context, let key, let value else { return }
                let collected = Unmanaged<Collected>.fromOpaque(context).takeUnretainedValue()
                collected.tags.append(Tag(key: String(cString: key), value: String(cString: value)))
            }, &properties)
        }
        guard status == 0 else { return nil }

        var tags = AudioFileTags()
        tags.tags = collected.tags
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
}
