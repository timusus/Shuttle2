import Foundation
import Shared

/// One labelled value in Song Info.
struct SongInfoRow: Equatable, Identifiable {
    let label: String
    let value: String
    var id: String { label }
}

/// A titled group of rows in Song Info; a section with no rows isn't made.
struct SongInfoSection: Equatable, Identifiable {
    let title: String
    let rows: [SongInfoRow]
    var id: String { title }
}

/// What Song Info shows for a song, in order: Track, Audio, Library, Source. Fields the song doesn't have are left out.
enum SongInfoSections {
    static func make(for song: Song, locale: Locale = .current, timeZone: TimeZone = .current) -> [SongInfoSection] {
        let quality = AudioQuality(song: song)
        let codec = song.audioCodec.map { $0.trimmingCharacters(in: .whitespaces).uppercased() }
        let container = AudioQuality.formatName(mimeType: song.mimeType)

        let track = rows([
            ("Title", song.name),
            ("Artist", song.artists.isEmpty ? nil : song.artists.joined(separator: ", ")),
            ("Album", song.album),
            ("Album Artist", song.albumArtist),
            ("Track", song.track.map { String($0.intValue) }),
            ("Disc", song.disc.map { String($0.intValue) }),
            ("Year", song.date.map { String($0.year) }),
            ("Genre", song.genres.isEmpty ? nil : song.genres.joined(separator: ", ")),
            ("Duration", song.duration > 0 ? duration(milliseconds: Int64(song.duration)) : nil),
        ])
        let audio = rows([
            ("Quality", quality.summary(locale: locale)),
            ("Format", container),
            ("Codec", codec == container ? nil : codec),
            ("Bit Rate", quality.bitRateText(locale: locale)),
            ("Bit Depth", quality.bitDepth.map { "\($0)-bit" }),
            ("Sample Rate", quality.sampleRate.map { AudioQuality.kilohertz($0, locale: locale) + " kHz" }),
            ("Channels", song.channelCount.map { String($0.intValue) }),
            ("File Size", song.size > 0 ? ByteCountFormatter.string(fromByteCount: song.size, countStyle: .file) : nil),
        ])
        let library = rows([
            ("Plays", song.playCount.formatted(.number.locale(locale))),
            ("Last Played", song.lastPlayed.map { date($0, locale: locale, timeZone: timeZone) }),
            ("Date Added", song.dateAdded.map { date($0, locale: locale, timeZone: timeZone) }),
        ])
        let source = rows([sourceRow(for: song)])

        return [
            ("Track", track), ("Audio", audio), ("Library", library), ("Source", source),
        ].compactMap { $0.1.isEmpty ? nil : SongInfoSection(title: $0.0, rows: $0.1) }
    }

    /// A local song's path, or the server a remote song streams from.
    private static func sourceRow(for song: Song) -> (String, String?) {
        if song.mediaProvider.remote { return ("Server", song.mediaProvider.name) }
        return ("Path", displayPath(song.path))
    }

    /// A Storage Access Framework document URI as the path inside its volume; any other path as it is.
    static func displayPath(_ path: String) -> String? {
        guard !path.isEmpty else { return nil }
        guard path.hasPrefix("content://"), path.contains("/document/"), let decoded = path.removingPercentEncoding else { return path }
        return decoded.split(separator: ":", omittingEmptySubsequences: false).last.map(String.init) ?? path
    }

    private static func rows(_ pairs: [(String, String?)]) -> [SongInfoRow] {
        pairs.compactMap { label, value in
            guard let value, !value.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
            return SongInfoRow(label: label, value: value)
        }
    }

    private static func duration(milliseconds: Int64) -> String {
        let total = Int(milliseconds / 1000)
        let (hours, minutes, seconds) = (total / 3600, total % 3600 / 60, total % 60)
        return hours > 0 ? String(format: "%d:%02d:%02d", hours, minutes, seconds) : String(format: "%d:%02d", minutes, seconds)
    }

    private static func date(_ instant: KotlinInstant, locale: Locale, timeZone: TimeZone) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(instant.toEpochMilliseconds()) / 1000)
        return date.formatted(Date.FormatStyle(date: .abbreviated, time: .shortened, locale: locale, timeZone: timeZone))
    }
}
