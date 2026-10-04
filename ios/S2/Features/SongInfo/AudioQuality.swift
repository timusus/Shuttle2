import Foundation
import Shared

/// A song's audio format as a short badge label, "FLAC · 24/96 kHz" or "MP3 · 320 kbps" (Now Playing, album headers); Song Info's
/// own rows come from the shared `infoSections()`. Pure values, so it tests without Kotlin.
struct AudioQuality: Equatable {
    /// "FLAC", "MP3", "ALAC": the source codec when the provider reports one, else the MIME type's subtype.
    /// (The shared `formatName(mimeType)` is module-internal, so it isn't callable from Swift.)
    let format: String?
    let bitDepth: Int?
    /// In hertz.
    let sampleRate: Int?
    /// In kilobits per second.
    let bitRate: Int?

    init(codec: String? = nil, mimeType: String? = nil, bitDepth: Int? = nil, sampleRate: Int? = nil, bitRate: Int? = nil) {
        let codecName = codec?.trimmingCharacters(in: .whitespaces).uppercased()
        self.format = (codecName?.isEmpty == false ? codecName : nil) ?? mimeType.flatMap(Self.formatName)
        self.bitDepth = bitDepth.flatMap { $0 > 0 ? $0 : nil }
        self.sampleRate = sampleRate.flatMap { $0 > 0 ? $0 : nil }
        self.bitRate = bitRate.flatMap { $0 > 0 ? $0 : nil }
    }

    init(song: Song) {
        self.init(
            codec: song.audioCodec,
            mimeType: song.mimeType,
            bitDepth: song.bitDepth.map { Int(truncating: $0) },
            sampleRate: song.sampleRate.map { Int(truncating: $0) },
            bitRate: song.bitRate.map { Int(truncating: $0) }
        )
    }

    /// Whether the format keeps the source's bit depth: only then does a bit depth mean anything to a listener
    /// (TagLib reports 16 for AAC/M4A too).
    var isLossless: Bool {
        guard let format else { return false }
        return Self.losslessFormats.contains(format) || format.hasPrefix("DSD")
    }

    private static let losslessFormats: Set<String> = ["FLAC", "ALAC", "WAV", "WAVE", "AIFF", "AIF", "APE", "WAVPACK", "WV", "DSF", "DFF"]

    /// A short label for a badge: "FLAC · 24/96 kHz" (bit depth / kHz) for lossless formats, "MP3 · 320 kbps" for lossy ones.
    func badge(locale: Locale = .current) -> String? {
        let detail: String?
        if isLossless, let bitDepth, let sampleRate {
            detail = "\(bitDepth)/" + Self.kilohertz(sampleRate, locale: locale) + " kHz"
        } else {
            detail = bitRate.map { "\($0) kbps" }
        }
        let parts = [format, detail].compactMap { $0 }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    /// The badge the songs share, for an album's header: nil when they're mixed (or any has no format), so a header
    /// never claims a quality only some of the tracks have.
    static func sharedBadge(of songs: [Song], locale: Locale = .current) -> String? {
        let badges = Set(songs.map { AudioQuality(song: $0).badge(locale: locale) })
        guard badges.count == 1, let badge = badges.first else { return nil }
        return badge
    }

    /// 44100 as "44.1", 22050 as "22.05", 96000 as "96".
    static func kilohertz(_ hertz: Int, locale: Locale = .current) -> String {
        (Double(hertz) / 1000).formatted(.number.precision(.fractionLength(0...2)).locale(locale))
    }

    /// A MIME type as the format people know it: "audio/flac" as "FLAC", "audio/mpeg" as "MP3"; nil when it names none.
    static func formatName(mimeType: String) -> String? {
        let afterSlash = mimeType.split(separator: "/", maxSplits: 1).dropFirst().first.map(String.init) ?? ""
        var subtype = afterSlash.split(separator: ";").first.map(String.init) ?? ""
        subtype = subtype.trimmingCharacters(in: .whitespaces).lowercased()
        if subtype.hasPrefix("x-") { subtype.removeFirst(2) }
        switch subtype {
        case "", "*": return nil
        case "mpeg", "mp3": return "MP3"
        case "mp4", "m4a", "mp4a-latm": return "M4A"
        case "vorbis": return "OGG"
        default: return subtype.uppercased()
        }
    }
}
