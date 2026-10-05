import Foundation

/// The library categories under Library (Android's `LibraryTab`, minus `Folders`: hidden on iOS until
/// local files land, `docs/architecture/ios-port/phase-5-ios-app.md` section 3). Order matches the
/// compact root's list and the sidebar's `TabSection` on regular and wide (`AppShell`).
enum LibraryCategory: String, Hashable, Codable, CaseIterable {
    case songs
    case albums
    case albumArtists
    case genres
    case playlists

    var title: String {
        switch self {
        case .songs: "Songs"
        case .albums: "Albums"
        case .albumArtists: "Artists"
        case .genres: "Genres"
        case .playlists: "Playlists"
        }
    }

    var systemImage: String {
        switch self {
        case .songs: "music.note"
        case .albums: "square.stack"
        case .albumArtists: "person.2"
        case .genres: "guitars"
        case .playlists: "music.note.list"
        }
    }
}

/// The shell's typed navigation destinations, mirroring `ui/shell/Routes.kt` and
/// `screens/library/LibraryRoutes.kt` case for case (`ios-port/phase-5-ios-app.md` section 2). A `Route`
/// carries keys, never models, same as Android's `NavKey`s, and is `Codable` so a path can be persisted
/// under `@SceneStorage` (`Navigator.StoredPath`).
enum Route: Hashable, Codable {
    /// Compact only: the Library root pushes one of these. Regular and wide show a category directly as
    /// its own sidebar entry and stack instead (`AppShell`).
    case libraryCategory(LibraryCategory)
    case album(albumKey: String?, albumArtistKey: String?, albumIdentity: String? = nil)
    case albumArtist(albumArtistKey: String?)
    case genre(name: String)
    case playlist(id: Int64)
    case smartPlaylist(id: String)
    /// Sources (Android's `SourcesRoute`): the media servers, pushed from Settings' Sources row and the Library's
    /// empty state.
    case sources
    /// A connected server's detail, pushed from its row in Sources: `MediaProviderType.name`, since a Kotlin enum isn't
    /// `Codable`.
    case server(type: String)
    /// The equalizer (Android's `EqualizerRoute`), pushed from Settings' Equalizer row.
    case equalizer
    /// Last.fm scrobbling (Android's `ScrobblingRoute`), pushed from Settings' Scrobbling row.
    case scrobbling
    /// Downloads (storage used, Remove All, running and failed downloads), pushed from Settings' Downloads row.
    case downloads

    /// The `ViewModelCache` key for the screen this route resolves to (`ios.md`, "Swift ↔ Kotlin").
    var cacheKey: String {
        switch self {
        case .libraryCategory(let category): "libraryCategory:\(category.rawValue)"
        case .album(let albumKey, let albumArtistKey, let albumIdentity): "album:\(albumKey ?? "")|\(albumArtistKey ?? "")" + (albumIdentity.map { "|\($0)" } ?? "")
        case .albumArtist(let albumArtistKey): "albumArtist:\(albumArtistKey ?? "")"
        case .genre(let name): "genre:\(name)"
        case .playlist(let id): "playlist:\(id)"
        case .smartPlaylist(let id): "smartPlaylist:\(id)"
        case .sources: "sources"
        case .server(let type): "server:\(type)"
        case .equalizer: "equalizer"
        case .scrobbling: "scrobbling"
        case .downloads: "downloads"
        }
    }
}
