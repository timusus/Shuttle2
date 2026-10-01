import Foundation
import Shared

/// A song's audio format as people name it: "FLAC · 24-bit / 96 kHz · 2,304 kbps" for Song Info, "FLAC 24/96" for
/// a badge (Now Playing, album headers). Pure values, so it formats anything that has them and tests without Kotlin.
struct AudioQuality: Equatable {
    /// "FLAC", "MP3", "ALAC": the source codec when the provider reports one, else the MIME type's subtype.
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

    /// "24-bit / 96 kHz", or whichever half the song has.
    func resolution(locale: Locale = .current) -> String? {
        let parts = [bitDepth.map { "\($0)-bit" }, sampleRate.map { Self.kilohertz($0, locale: locale) + " kHz" }].compactMap { $0 }
        return parts.isEmpty ? nil : parts.joined(separator: " / ")
    }

    /// "2,304 kbps".
    func bitRateText(locale: Locale = .current) -> String? {
        bitRate.map { $0.formatted(.number.locale(locale)) + " kbps" }
    }

    /// "FLAC · 24-bit / 96 kHz · 2,304 kbps": the parts the song has, nil when it has none.
    func summary(locale: Locale = .current) -> String? {
        let parts = [format, resolution(locale: locale), bitRateText(locale: locale)].compactMap { $0 }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    /// A short label for a badge: "FLAC 24/96" (bit depth / kHz) for lossless-style files, "MP3 320" (kbps) otherwise.
    func badge(locale: Locale = .current) -> String? {
        let detail: String?
        if let bitDepth, let sampleRate {
            detail = "\(bitDepth)/" + Self.kilohertz(sampleRate, locale: locale)
        } else {
            detail = bitRate.map { String($0) }
        }
        let parts = [format, detail].compactMap { $0 }
        return parts.isEmpty ? nil : parts.joined(separator: " ")
    }

    /// 44100 as "44.1", 96000 as "96".
    static func kilohertz(_ hertz: Int, locale: Locale = .current) -> String {
        (Double(hertz) / 1000).formatted(.number.precision(.fractionLength(0...1)).locale(locale))
    }

    /// A MIME type as the format people know it: "audio/flac" as "FLAC", "audio/mpeg" as "MP3"; nil when it names none.
    static func formatName(mimeType: String) -> String? {
        let afterSlash = mimeType.split(separator: "/", maxSplits: 1).dropFirst().first.map(String.init) ?? ""
        var subtype = afterSlash.split(separator: ";").first.map(String.init) ?? ""
        subtype = subtype.trimmingCharacters(in: .whitespaces).lowercased()
        if subtype.hasPrefix("x-") { subtype.removeFirst(2) }
        switch subtype {
        case "": return nil
        case "mpeg", "mp3": return "MP3"
        case "mp4", "m4a", "mp4a-latm": return "M4A"
        case "vorbis": return "OGG"
        default: return subtype.uppercased()
        }
    }
}
