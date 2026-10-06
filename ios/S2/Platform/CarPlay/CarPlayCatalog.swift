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
    /// The SF Symbol drawn until (or instead of) its artwork.
    var symbol: String?
    /// Whether it has something that plays with no network: a song on this device or downloaded.
    var playableOffline = true
}

/// One of Home's shelves, in the phone's order: its items as a row of artwork, and the whole shelf behind it. Jump
/// Back In's `resumes`: each item carries on where it was left rather than starting over.
struct CarPlayHomeSection: Equatable {
    let id: String
    let header: String
    let entries: [CarPlayEntry]
    var resumes = false
}

/// Library's lists, in the order the Library tab offers them.
enum CarPlayLibraryCategory: String, CaseIterable {
    case artists
    case albums
    case songs
    case genres

    var title: String {
        switch self {
        case .artists: CarPlayText.artists
        case .albums: CarPlayText.albums
        case .songs: CarPlayText.songs
        case .genres: CarPlayText.genres
        }
    }

    var symbol: String {
        switch self {
        case .artists: "music.mic"
        case .albums: "square.stack"
        case .songs: "music.note"
        case .genres: "guitars"
        }
    }
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
    /// Plays the entry (a Home item) from its start.
    case play(id: String)
    /// Carries the entry (a Jump Back In item) on where its queue was left.
    case resume(id: String)
    /// Pushes the whole of one of Home's shelves.
    case openShelf(id: String)
    /// An informational row.
    case none
}

/// One image of an image row (a Home shelf): an item's artwork with its title, which acts when tapped.
struct CarPlayImage: Equatable {
    let id: String
    let title: String
    var artwork: ArtworkSource?
    var symbol: String?
    let action: CarPlayRowAction
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
    /// A row of artwork rather than a list row (a Home shelf); `action` is the row's own, each image has its own.
    var images: [CarPlayImage] = []
    let action: CarPlayRowAction

    var isSelectable: Bool { action != .none }
}

struct CarPlaySectionModel: Equatable {
    var header: String?
    var rows: [CarPlayRow]
}

/// The CarPlay text, from the CarPlay strings table.
enum CarPlayText {
    static func text(_ key: String) -> String {
        String(localized: String.LocalizationValue(key), table: "CarPlay")
    }

    static var home: String { text("carplay_tab_home") }
    static var playlists: String { text("carplay_tab_playlists") }
    static var library: String { text("carplay_tab_library") }
    static var albums: String { text("carplay_albums") }
    static var artists: String { text("carplay_artists") }
    static var songs: String { text("carplay_songs") }
    static var genres: String { text("carplay_genres") }
    static var autoPlaylists: String { text("carplay_auto_playlists") }
    static var shuffleAll: String { text("carplay_shuffle_all") }
    static var shuffle: String { text("carplay_shuffle") }
    static var loading: String { text("carplay_loading") }
    static var unavailable: String { text("carplay_unavailable") }
    static var unavailableDetail: String { text("carplay_unavailable_detail") }
    static var libraryEmpty: String { text("carplay_library_empty") }
    static var libraryEmptyDetail: String { text("carplay_library_empty_detail") }
    static var homeEmpty: String { text("carplay_home_empty") }
    static var homeEmptyDetail: String { text("carplay_home_empty_detail") }
    static var offlineEmpty: String { text("carplay_offline_empty") }
    static var offlineEmptyDetail: String { text("carplay_offline_empty_detail") }
    static var noAlbums: String { text("carplay_no_albums") }
    static var noArtists: String { text("carplay_no_artists") }
    static var noPlaylists: String { text("carplay_no_playlists") }
    static var noGenres: String { text("carplay_no_genres") }
    static var noSongs: String { text("carplay_no_songs") }
    static var unknown: String { text("carplay_unknown") }
    static var upNext: String { text("carplay_up_next") }
    static var queueEmpty: String { text("carplay_queue_empty") }
    static var upgrade: String { text("carplay_pro_upgrade") }
    static var upgradeDetail: String { text("carplay_pro_upgrade_detail") }
}

enum CarPlayCatalog {
    /// The most rows a library list or song list shows. Driving, nobody scrolls further; the car's own limit
    /// (`CPListTemplate.maximumItemCount`) may be lower and wins.
    static let listRowLimit = 100
    /// The most images a Home shelf offers; the car's own limit (`CPListImageRowItem.maximumImageCount`) may be
    /// lower and wins, and a narrow screen draws fewer still.
    static let shelfImageLimit = 8
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

    /// CarPlay's root without Shuttle Music Pro (#946): one inert row, since the paywall only opens on the phone.
    static func upgrade() -> [CarPlaySectionModel] {
        message(CarPlayText.upgrade, detail: CarPlayText.upgradeDetail)
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
        return [CarPlaySectionModel(header: nil, rows: entries.prefix(min(limit, listRowLimit)).map(entryRow))]
    }

    private static func entryRow(_ entry: CarPlayEntry) -> CarPlayRow {
        CarPlayRow(
            id: entry.id,
            title: entry.title,
            subtitle: entry.subtitle,
            accessory: .disclosure,
            artwork: entry.artwork,
            symbol: entry.symbol,
            action: .open(id: entry.id)
        )
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

    /// Home: each shelf, in the phone's order, as a row of artwork under its name, which plays an item when its image
    /// is tapped (Jump Back In's resumes) and opens the whole shelf when the row is: one row a shelf, so the car's
    /// limits (`rowLimit`, `imageLimit`) trim from the end. Empty shelves are hidden, and
    /// offline so is every item with nothing that plays without a network. Shuffle All comes after the shelves, only if
    /// the car has room for it, so it never pushes a shelf down, and never offline, where most of what it would shuffle
    /// can't play. Nothing played yet still offers Shuffle All, with a line saying how Home fills in.
    static func home(
        _ sections: [CarPlayHomeSection],
        offline: Bool = false,
        imageLimit: Int = shelfImageLimit,
        rowLimit: Int = listRowLimit
    ) -> [CarPlaySectionModel] {
        let shelves = sections.map { available($0, offline: offline) }.filter { !$0.entries.isEmpty }
        let shuffle = shuffleRow(title: CarPlayText.shuffleAll)
        guard !shelves.isEmpty else {
            if offline { return message(CarPlayText.offlineEmpty, detail: CarPlayText.offlineEmptyDetail) }
            let empty = CarPlayRow(id: messageRowId, title: CarPlayText.homeEmpty, subtitle: CarPlayText.homeEmptyDetail, action: .none)
            return [CarPlaySectionModel(header: nil, rows: [shuffle, empty])]
        }
        let images = max(min(imageLimit, shelfImageLimit), 1)
        var rows = shelves.prefix(max(rowLimit, 1)).map { shelf in
            CarPlayRow(
                id: "shelf-\(shelf.id)",
                title: shelf.header,
                images: shelf.entries.prefix(images).map { entry in
                    CarPlayImage(id: entry.id, title: entry.title, artwork: entry.artwork, symbol: entry.symbol, action: action(entry, in: shelf))
                },
                action: .openShelf(id: shelf.id)
            )
        }
        if !offline, rows.count < rowLimit {
            rows.append(shuffle)
        }
        return [CarPlaySectionModel(header: nil, rows: rows)]
    }

    /// The whole of one of Home's shelves, pushed from its row: an item a row, each playing (Jump Back In's resuming,
    /// with how far through it is), offline only the ones that can play.
    static func shelf(_ section: CarPlayHomeSection, offline: Bool = false, limit: Int = listRowLimit) -> [CarPlaySectionModel] {
        let shelf = available(section, offline: offline)
        guard !shelf.entries.isEmpty else {
            return offline ? message(CarPlayText.offlineEmpty, detail: CarPlayText.offlineEmptyDetail) : message(CarPlayText.homeEmpty)
        }
        let rows = shelf.entries.prefix(min(limit, listRowLimit)).map { entry in
            CarPlayRow(
                id: entry.id,
                title: entry.title,
                subtitle: entry.subtitle,
                progress: shelf.resumes ? entry.progress.map(quantised) : nil,
                artwork: entry.artwork,
                symbol: entry.symbol,
                action: action(entry, in: shelf)
            )
        }
        return [CarPlaySectionModel(header: nil, rows: Array(rows))]
    }

    /// The Library tab: a row for each of its lists, which pushes it.
    static func library() -> [CarPlaySectionModel] {
        let rows = CarPlayLibraryCategory.allCases.map { category in
            CarPlayRow(id: category.rawValue, title: category.title, accessory: .disclosure, symbol: category.symbol, action: .open(id: category.rawValue))
        }
        return [CarPlaySectionModel(header: nil, rows: rows)]
    }

    /// The Playlists tab, as the phone's: the auto playlists, then the user's own, each opening its songs. `limit`
    /// counts both. Nothing to show says so.
    static func playlists(smart: [CarPlayEntry], playlists: [CarPlayEntry], limit: Int = listRowLimit) -> [CarPlaySectionModel] {
        guard !smart.isEmpty || !playlists.isEmpty else { return message(CarPlayText.noPlaylists) }
        let cap = min(limit, listRowLimit)
        let smartRows = smart.prefix(cap).map(entryRow)
        let ownRows = playlists.prefix(max(cap - smartRows.count, 0)).map(entryRow)
        var result: [CarPlaySectionModel] = []
        if !smartRows.isEmpty { result.append(CarPlaySectionModel(header: CarPlayText.autoPlaylists, rows: smartRows)) }
        if !ownRows.isEmpty { result.append(CarPlaySectionModel(header: CarPlayText.playlists, rows: ownRows)) }
        return result
    }

    private static func available(_ section: CarPlayHomeSection, offline: Bool) -> CarPlayHomeSection {
        guard offline else { return section }
        return CarPlayHomeSection(id: section.id, header: section.header, entries: section.entries.filter(\.playableOffline), resumes: section.resumes)
    }

    private static func action(_ entry: CarPlayEntry, in section: CarPlayHomeSection) -> CarPlayRowAction {
        section.resumes ? .resume(id: entry.id) : .play(id: entry.id)
    }

    /// Progress to the percent, so a playing item's position doesn't redraw Home every tick.
    static func quantised(_ progress: Double) -> Double {
        (min(max(progress, 0), 1) * 100).rounded() / 100
    }
}
