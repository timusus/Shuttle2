import Shared
import SwiftUI

/// The sort key a song row shows after its credits, where it's the point of the list's order (#702): a play count,
/// how long ago it last played, or the date it was added.
enum SongRowKey: Equatable {
    case plays
    case lastPlayed
    case dateAdded

    /// The Library's song sort, whose key the row adds: plays under Most Played, the date under Date Added.
    init?(sortOrder: SongSortOrder?) {
        switch sortOrder {
        case .playCount: self = .plays
        case .dateAdded: self = .dateAdded
        default: return nil
        }
    }

    /// The artist detail's song order: Most Played shows its play counts.
    init?(artistSortOrder: ArtistSongSortOrder) {
        guard artistSortOrder == .mostPlayed else { return nil }
        self = .plays
    }

    /// The key a smart playlist is ordered by (the order its query sorts in); nil for Favourites, whose rows show
    /// their heart instead.
    init?(smartPlaylist id: SmartPlaylistId) {
        switch id.id {
        case "most-played": self = .plays
        case "history": self = .lastPlayed
        case "recently-added": self = .dateAdded
        default: return nil
        }
    }

    /// "12 plays", "3 days ago", "12 Mar 2026"; nil when the song has no such value.
    func text(for song: Song, now: Date = .now) -> String? {
        switch self {
        case .plays:
            guard song.playCount > 0 else { return nil }
            return song.playCount == 1 ? "1 play" : "\(song.playCount) plays"
        case .lastPlayed:
            guard let played = song.lastCompleted ?? song.lastPlayed else { return nil }
            let date = Date(timeIntervalSince1970: TimeInterval(played.toEpochMilliseconds()) / 1000)
            return RelativeDateTimeFormatter.songRow.localizedString(for: date, relativeTo: now)
        case .dateAdded:
            return libraryDateAdded(song.dateAdded)
        }
    }
}

private extension RelativeDateTimeFormatter {
    static let songRow: RelativeDateTimeFormatter = {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .full
        return formatter
    }()
}

/// A song as a list row, for every list of songs: Library > Songs, Search, and the artist, genre, playlist and smart
/// playlist screens. One row, configured by where it sits:
/// - `omittingArtist` drops the screen's own artist from the credits (artist detail), leaving the album, or only
///   the other artists credited when they differ;
/// - `key` adds the sort key where it's the point (`SongRowKey`);
/// - the trailing side holds a small heart for a favourite, then the duration, after a badge when it's on the device (#853) (under the title at accessibility text sizes).
struct SongRow: View {
    let song: Song
    var playback: MediaRowPlayback = .none
    var key: SongRowKey?
    var omittingArtist: String?
    var artworkSize: CGFloat

    @Environment(\.downloadBadges) private var downloadBadges

    /// The Library's sort, which adds its key to the subtitle: a play count, or the date added.
    init(song: Song, playback: MediaRowPlayback = .none, sortOrder: SongSortOrder? = nil, key: SongRowKey? = nil, omittingArtist: String? = nil, artworkSize: CGFloat = ArtworkSize.row) {
        self.song = song
        self.playback = playback
        self.key = key ?? SongRowKey(sortOrder: sortOrder)
        self.omittingArtist = omittingArtist
        self.artworkSize = artworkSize
    }

    var body: some View {
        MediaRow(
            song.name ?? "Unknown",
            subtitle: Self.subtitle(song, key: key, omittingArtist: omittingArtist),
            artwork: .song(song),
            artworkSize: artworkSize,
            playback: playback,
            titleIdentifier: "songRow.title"
        ) {
            HStack(spacing: Spacing.xsmall) {
                if song.isFavourite {
                    Image(systemName: "heart.fill")
                        .accessibilityLabel("Favourite")
                        .accessibilityIdentifier("songRow.favourite")
                }
                if let badge = downloadBadges[song.path] {
                    DownloadBadgeView(badge: badge)
                }
                SongDurationText(durationMs: Int64(song.duration))
            }
            .imageScale(.small)
            .textRole(.rowMeta)
            .foregroundStyle(.s2TextSecondary)
        }
    }

    /// The credits, the album and the key, joined with " · ".
    static func subtitle(_ song: Song, key: SongRowKey? = nil, omittingArtist: String? = nil) -> String {
        [credit(song, omitting: omittingArtist), song.album, key.flatMap { $0.text(for: song) }]
            .compactMap { $0 }
            .filter { !$0.isEmpty }
            .joined(separator: " · ")
    }

    /// The artists credited on the song; with `omitting`, a screen's own artist leaves it, so only the others show
    /// (nil when they're the only one).
    static func credit(_ song: Song, omitting: String?) -> String? {
        let all = song.friendlyArtistName ?? song.albumArtist
        guard let omitting = omitting.map(sameArtistKey), !omitting.isEmpty else { return all }
        let others = song.artists.filter { sameArtistKey($0) != omitting }
        if others.count == song.artists.count { return all.map(sameArtistKey) == omitting ? nil : all }
        return others.isEmpty ? nil : others.joined(separator: ", ")
    }

    /// An artist name as `friendlyArtistName` compares it: trimmed, lower-cased, without a leading "The ".
    private static func sameArtistKey(_ name: String) -> String {
        let key = name.trimmingCharacters(in: .whitespaces).lowercased()
        return key.hasPrefix("the ") ? String(key.dropFirst(4)).trimmingCharacters(in: .whitespaces) : key
    }
}
