import Shared
import SwiftUI

/// Whether `song` is the one playing, as a row shows it: the `currentSong` a detail ViewModel reports, and whether
/// the player is playing (`PlayerBinding`).
func rowPlayback(_ song: Song, current: Song?, isPlaying: Bool) -> MediaRowPlayback {
    guard let current, current.id == song.id else { return .none }
    return isPlaying ? .playing : .paused
}

/// A hero's eyebrow line: its parts joined with " · ", skipping empty ones.
func eyebrow(_ parts: String?...) -> String {
    parts.compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
}

/// The total running time of `songs`, "43 min" or "1 hr 3 min"; nil when unknown (zero).
func totalDuration(_ songs: [Song], locale: Locale = .current) -> String? {
    runtime(ms: songs.reduce(Int64(0)) { $0 + Int64($1.duration) }, locale: locale)
}

/// A running time of `ms` milliseconds as "43 min" or "1 hr 3 min"; nil under a minute (unknown or negligible).
func runtime(ms: Int64, locale: Locale = .current) -> String? {
    guard ms >= 60_000 else { return nil }
    return Duration.milliseconds(ms).formatted(.units(allowed: [.hours, .minutes], width: .abbreviated).locale(locale))
}

/// A song's length as VoiceOver says it: "3 minutes, 20 seconds".
func spokenDuration(ms: Int64, locale: Locale = .current) -> String {
    Duration.milliseconds(ms).formatted(.units(allowed: [.hours, .minutes, .seconds], width: .wide).locale(locale))
}

/// A noun with a generated plural of the same name (`ios/scripts/generate-strings.py`, from Android's plurals).
enum CountNoun: String {
    case song = "songsPlural"
    case album = "albumsPlural"
}

/// "1 song" / "N songs" (or albums) in the app's language, the pluralisation every subtitle repeats.
func pluralized(_ count: Int, _ noun: CountNoun) -> String { localizedPlural(noun.rawValue, count) }

/// The `.stringsdict` plural [key] for [count] (its first argument, `%1$d`), then [args] (`%2$@`...).
func localizedPlural(_ key: String, _ count: Int, _ args: String...) -> String {
    let format = Bundle.main.localizedString(forKey: key, value: nil, table: nil)
    return String(format: format, locale: .current, arguments: [Int32(clamping: count)] + args as [CVarArg])
}

extension PlayerBinding {
    /// Whether the player is playing, for a detail screen's playing row. Reads only the mini player's state, so a
    /// progress tick doesn't redraw the screen.
    var isPlaying: Bool { miniPlayer.isPlaying }
}
