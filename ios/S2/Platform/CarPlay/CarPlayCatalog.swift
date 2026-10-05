import Foundation

// What CarPlay lists (#692), as values: the rows of each tab and pushed list, built from plain inputs so tests need
// neither CarPlay nor Kotlin. `CarPlaySceneDelegate` maps the shared view models' state into these inputs and
// `CarPlayListRenderer` draws the result. Ported from Shuttle Podcasts' catalog.

/// A song as a CarPlay list shows it.
struct CarPlaySong: Equatable {
    let id: Int64
    let title: String
    let subtitle: String?
    var artwork: ArtworkSource?
}

/// An album, artist, playlist or Home item as a CarPlay list shows it: a row that opens or plays the thing `id` names.
struct CarPlayEntry: Equatable {
    let id: String
    let title: String
    let subtitle: String?
    var artwork: ArtworkSource?
    /// How far through it the listener got (Jump Back In), 0...1.
    var progress: Double?
}

/// One of Home's sections. Jump Back In's is `keepsAll`: what the listener was in the middle of is never trimmed to
/// fit the car's limits.
struct CarPlayHomeSection: Equatable {
    let id: String
    let header: String
    let entries: [CarPlayEntry]
    var keepsAll = false
}

enum CarPlayAccessory: Equatable {
    case none
    case disclosure
}

/// What tapping a row does. The scene delegate resolves ids against the list's current state.
enum CarPlayRowAction: Equatable {
    /// Plays the list's songs from this one.
    case playSong(index: Int)
    /// Shuffles the list's songs (or the library, on Home and Songs).
    case shuffle
    /// Pushes the entry's songs.
    case open(id: String)
    /// Plays the entry (a Home item).
    case play(id: String)
    /// An informational row.
    case none
}

struct CarPlayRow: Equatable {
    let id: String
    let title: String
    var subtitle: String?
    var progress: Double?
    var isPlaying = false
    var accessory: CarPlayAccessory = .none
    var artwork: ArtworkSource?
    /// An SF Symbol drawn when there's no artwork (Shuffle's row).
    var symbol: String?
    let action: CarPlayRowAction

    var isSelectable: Bool { action != .none }
}

struct CarPlaySectionModel: Equatable {
    var header: String?
    var rows: [CarPlayRow]
}

/// How much a template may hold, from the car (`CPListTemplate.maximumItemCount` / `maximumSectionCount`).
struct CarPlayBudget: Equatable {
    let maxItems: Int
    let maxSections: Int

    static let unbounded = CarPlayBudget(maxItems: .max, maxSections: .max)
}

/// The CarPlay text, from the CarPlay strings table.
enum CarPlayText {
    static func text(_ key: String) -> String {
        String(localized: String.LocalizationValue(key), table: "CarPlay")
    }

    static var home: String { text("carplay_tab_home") }
    static var albums: String { text("carplay_tab_albums") }
    static var artists: String { text("carplay_tab_artists") }
    static var playlists: String { text("carplay_tab_playlists") }
    static var songs: String { text("carplay_tab_songs") }
    static var shuffleAll: String { text("carplay_shuffle_all") }
    static var shuffle: String { text("carplay_shuffle") }
    static var loading: String { text("carplay_loading") }
    static var unavailable: String { text("carplay_unavailable") }
    static var unavailableDetail: String { text("carplay_unavailable_detail") }
    static var libraryEmpty: String { text("carplay_library_empty") }
    static var libraryEmptyDetail: String { text("carplay_library_empty_detail") }
    static var homeEmpty: String { text("carplay_home_empty") }
    static var homeEmptyDetail: String { text("carplay_home_empty_detail") }
    static var noAlbums: String { text("carplay_no_albums") }
    static var noArtists: String { text("carplay_no_artists") }
    static var noPlaylists: String { text("carplay_no_playlists") }
    static var noSongs: String { text("carplay_no_songs") }
    static var unknown: String { text("carplay_unknown") }
    static var upNext: String { text("carplay_up_next") }
    static var queueEmpty: String { text("carplay_queue_empty") }
}

enum CarPlayCatalog {
    /// The most rows a library list or song list shows. Driving, nobody scrolls further; the car's own limit
    /// (`CPListTemplate.maximumItemCount`) may be lower and wins.
    static let listRowLimit = 100
    /// The most rows each of Home's suggestion sections shows.
    static let homeSectionRowLimit = 8
    static let shuffleRowId = "shuffle"
    static let messageRowId = "message"

    /// One inert row saying why a list is empty.
    static func message(_ title: String, detail: String? = nil) -> [CarPlaySectionModel] {
        [CarPlaySectionModel(header: nil, rows: [CarPlayRow(id: messageRowId, title: title, subtitle: detail, action: .none)])]
    }

    static func loading() -> [CarPlaySectionModel] {
        message(CarPlayText.loading)
    }

    static func unavailable() -> [CarPlaySectionModel] {
        message(CarPlayText.unavailable, detail: CarPlayText.unavailableDetail)
    }

    static func shuffleRow(title: String = CarPlayText.shuffle) -> CarPlayRow {
        CarPlayRow(id: shuffleRowId, title: title, symbol: "shuffle", action: .shuffle)
    }

    /// A list of songs: Shuffle first, then the songs, the playing one marked. `limit` counts Shuffle's row.
    static func songs(
        _ songs: [CarPlaySong],
        playingId: Int64? = nil,
        shuffleTitle: String = CarPlayText.shuffle,
        limit: Int = listRowLimit
    ) -> [CarPlaySectionModel] {
        guard !songs.isEmpty else { return message(CarPlayText.noSongs) }
        let cap = max(min(limit, listRowLimit) - 1, 0)
        let rows = songs.prefix(cap).enumerated().map { index, song in
            CarPlayRow(
                id: "\(index)-\(song.id)",
                title: song.title,
                subtitle: song.subtitle,
                isPlaying: song.id == playingId,
                artwork: song.artwork,
                action: .playSong(index: index)
            )
        }
        return [CarPlaySectionModel(header: nil, rows: [shuffleRow(title: shuffleTitle)] + rows)]
    }

    /// Albums, artists or playlists: one row each that opens its songs, capped at `limit`.
    static func entries(_ entries: [CarPlayEntry], empty: String, limit: Int = listRowLimit) -> [CarPlaySectionModel] {
        guard !entries.isEmpty else { return message(empty) }
        let rows = entries.prefix(min(limit, listRowLimit)).map { entry in
            CarPlayRow(
                id: entry.id,
                title: entry.title,
                subtitle: entry.subtitle,
                accessory: .disclosure,
                artwork: entry.artwork,
                action: .open(id: entry.id)
            )
        }
        return [CarPlaySectionModel(header: nil, rows: Array(rows))]
    }

    /// The queue, from Now Playing's Up Next: each song plays when tapped, the current one marked. No Shuffle row;
    /// the Now Playing screen has its own.
    static func queue(_ songs: [CarPlaySong], currentIndex: Int?, limit: Int = listRowLimit) -> [CarPlaySectionModel] {
        guard !songs.isEmpty else { return message(CarPlayText.queueEmpty) }
        // The list starts at the current song, so what plays next is in view
        let start = currentIndex.map { max(0, min($0, songs.count - 1)) } ?? 0
        let rows = songs.enumerated().dropFirst(start).prefix(min(limit, listRowLimit)).map { index, song in
            CarPlayRow(
                id: "\(index)-\(song.id)",
                title: song.title,
                subtitle: song.subtitle,
                isPlaying: index == currentIndex,
                artwork: song.artwork,
                action: .playSong(index: index)
            )
        }
        return [CarPlaySectionModel(header: nil, rows: Array(rows))]
    }

    /// Home: Shuffle All, then each section's items, which play when tapped. Jump Back In is kept whole; the other
    /// sections are capped at `homeSectionRowLimit` and then, in order, trimmed to fit the car's budget. Nothing
    /// played yet still offers Shuffle All, with a line saying how Home fills in.
    static func home(_ sections: [CarPlayHomeSection], budget: CarPlayBudget = .unbounded) -> [CarPlaySectionModel] {
        let shuffle = CarPlaySectionModel(header: nil, rows: [shuffleRow(title: CarPlayText.shuffleAll)])
        let filled = sections.filter { !$0.entries.isEmpty }
        guard !filled.isEmpty else {
            let empty = CarPlayRow(id: messageRowId, title: CarPlayText.homeEmpty, subtitle: CarPlayText.homeEmptyDetail, action: .none)
            return [CarPlaySectionModel(header: nil, rows: shuffle.rows + [empty])]
        }
        var itemsLeft = budget.maxItems - 1
        var sectionsLeft = budget.maxSections - 1
        // The kept sections take their room first, wherever they sit
        let kept = filled.filter(\.keepsAll)
        let keptRows = kept.reduce(0) { $0 + $1.entries.count }
        itemsLeft -= keptRows
        sectionsLeft -= kept.count
        var result = [shuffle]
        for section in filled {
            let rows: [CarPlayEntry]
            if section.keepsAll {
                rows = section.entries
            } else {
                guard itemsLeft > 0, sectionsLeft > 0 else { continue }
                rows = Array(section.entries.prefix(min(homeSectionRowLimit, itemsLeft)))
                itemsLeft -= rows.count
                sectionsLeft -= 1
            }
            result.append(CarPlaySectionModel(header: section.header, rows: rows.map { entry in
                CarPlayRow(
                    id: "\(section.id)-\(entry.id)",
                    title: entry.title,
                    subtitle: entry.subtitle,
                    progress: entry.progress.map(quantised),
                    artwork: entry.artwork,
                    action: .play(id: entry.id)
                )
            }))
        }
        return result
    }

    /// Progress to the percent, so a playing item's position doesn't redraw Home every tick.
    static func quantised(_ progress: Double) -> Double {
        (min(max(progress, 0), 1) * 100).rounded() / 100
    }
}
