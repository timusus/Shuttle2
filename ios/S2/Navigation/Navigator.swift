import SwiftUI

/// The shell's top-level destinations, as on Android (Home, Library, Search).
enum AppTab: Hashable, CaseIterable {
    case home
    case library
    case search

    var title: String {
        switch self {
        case .home: "Home"
        case .library: "Library"
        case .search: "Search"
        }
    }

    var systemImage: String {
        switch self {
        case .home: "house"
        case .library: "music.note.list"
        case .search: "magnifyingglass"
        }
    }

    /// The `ViewModelCache` key for this tab's root screen, retained alongside its path
    /// (`Navigator.retainViewModels`).
    var cacheKey: String {
        switch self {
        case .home: "tab:home"
        case .library: "tab:library"
        case .search: "tab:search"
        }
    }
}

/// What's selected at the root of the shell: a plain tab (compact, and Home/Search everywhere), or
/// (regular and wide) one of the library categories promoted into the sidebar in place of a "Library" tab
/// (`ios-port/phase-5-ios-app.md` section 2, "Library root: categories, not a tab strip").
enum RootSelection: Hashable {
    case tab(AppTab)
    case libraryCategory(LibraryCategory)
}

/// The shell's navigation state: the selected root, one path per tab, and (regular/wide) one path per
/// library category. Owned above the layout, so a tier change (rotation, Split View drag), which swaps
/// the tab bar for the sidebar, keeps every pushed screen.
///
/// Rules (unit tested in `NavigatorTests`, ported from Android's `AppNavigator`):
/// - Re-selecting the current root pops its path to root; there is no "back to the start tab" on iOS,
///   since the system back gesture is per stack.
/// - `open(_:)` pushes onto whichever path is currently selected.
/// - Every path change retains only the view models for routes still on some path
///   (`ViewModelCache.retainOnly`), clearing the rest.
@MainActor
@Observable
final class Navigator {
    private let viewModelCache: ViewModelCache

    private var _selection: RootSelection = .tab(.library)
    var homePath: [Route] = [] { didSet { retainViewModels() } }
    var libraryPath: [Route] = [] { didSet { retainViewModels() } }
    var searchPath: [Route] = [] { didSet { retainViewModels() } }
    private var libraryCategoryPaths: [LibraryCategory: [Route]] = [:] { didSet { retainViewModels() } }

    /// The wide inspector slot; empty until phase 6 wires Now Playing into it.
    var showsPlayerInspector = false

    init(viewModelCache: ViewModelCache = .shared) {
        self.viewModelCache = viewModelCache
    }

    /// Reading returns the current root. Writing re-selects: the same value pops that root's path to its
    /// root screen, a different value switches to it. Every root selection UI (compact `TabView`, the
    /// sidebar `TabView`/`NavigationSplitView`) binds straight to this.
    var selection: RootSelection {
        get { _selection }
        set {
            guard newValue == _selection else {
                _selection = newValue
                return
            }
            switch newValue {
            case .tab(let tab): setPath([], for: tab)
            case .libraryCategory(let category): libraryCategoryPaths[category] = []
            }
        }
    }

    /// The tab the compact `TabView` shows as selected: `.library` while a library category is selected,
    /// since a promoted category is still the Library tab's content.
    var selectedTab: AppTab {
        switch _selection {
        case .tab(let tab): tab
        case .libraryCategory: .library
        }
    }

    func selectTab(_ tab: AppTab) {
        selection = .tab(tab)
    }

    func selectLibraryCategory(_ category: LibraryCategory) {
        selection = .libraryCategory(category)
    }

    /// Pushes `route` onto whichever path is currently selected: a tab's on compact, or (regular/wide) a
    /// library category's own stack.
    func open(_ route: Route) {
        switch _selection {
        case .tab(let tab):
            setPath(path(for: tab) + [route], for: tab)
        case .libraryCategory(let category):
            libraryCategoryPaths[category, default: []].append(route)
        }
    }

    func path(for tab: AppTab) -> [Route] {
        switch tab {
        case .home: homePath
        case .library: libraryPath
        case .search: searchPath
        }
    }

    func path(for category: LibraryCategory) -> [Route] {
        libraryCategoryPaths[category, default: []]
    }

    /// A two-way binding onto a library category's own path, for its `NavigationStack` (regular/wide).
    func binding(for category: LibraryCategory) -> Binding<[Route]> {
        Binding(
            get: { self.libraryCategoryPaths[category, default: []] },
            set: { self.libraryCategoryPaths[category] = $0 }
        )
    }

    private func setPath(_ routes: [Route], for tab: AppTab) {
        switch tab {
        case .home: homePath = routes
        case .library: libraryPath = routes
        case .search: searchPath = routes
        }
    }

    private func retainViewModels() {
        var liveKeys = Set<String>()
        liveKeys.formUnion(AppTab.allCases.map(\.cacheKey))
        liveKeys.formUnion(LibraryCategory.allCases.map { Route.libraryCategory($0).cacheKey })
        liveKeys.formUnion(homePath.map(\.cacheKey))
        liveKeys.formUnion(libraryPath.map(\.cacheKey))
        liveKeys.formUnion(searchPath.map(\.cacheKey))
        for path in libraryCategoryPaths.values {
            liveKeys.formUnion(path.map(\.cacheKey))
        }
        viewModelCache.retainOnly(liveKeys)
    }
}

// MARK: - Tier normalization

extension Navigator {
    /// Folds a selected library category's own path into compact's `libraryPath` when the layout
    /// collapses to compact, and unfolds it back when the layout expands again, so a tier change
    /// (rotation, Split View drag) keeps every pushed screen instead of stranding it on the side the
    /// selection can no longer reach (`ContentView`'s compact `TabView` only tags `.tab(_)`).
    func normalizeSelection(for tier: LayoutTier) {
        if tier == .compact {
            guard case .libraryCategory(let category) = _selection else { return }
            libraryPath = [.libraryCategory(category)] + path(for: category)
            libraryCategoryPaths[category] = []
            _selection = .tab(.library)
        } else {
            guard case .tab(.library) = _selection,
                  case .libraryCategory(let category)? = libraryPath.first
            else { return }
            libraryCategoryPaths[category] = Array(libraryPath.dropFirst())
            libraryPath = []
            _selection = .libraryCategory(category)
        }
    }
}

// MARK: - @SceneStorage restoration

extension Navigator {
    /// `[Route]` encoded as JSON, so `@SceneStorage` (primitive `RawRepresentable` types only) can hold a
    /// tab's path across relaunch, as Android's back stacks survive process death.
    struct StoredPath: RawRepresentable, Equatable {
        var routes: [Route]

        init(_ routes: [Route] = []) {
            self.routes = routes
        }

        /// Malformed JSON or an unrecognised route (an older path, from before a route was added or
        /// renamed) decodes to empty rather than failing: a stale `@SceneStorage` value must never crash
        /// launch or block restoring the rest of the shell.
        init?(rawValue: String) {
            guard let data = rawValue.data(using: .utf8) else { return nil }
            self.routes = (try? JSONDecoder().decode([Route].self, from: data)) ?? []
        }

        var rawValue: String {
            guard let data = try? JSONEncoder().encode(routes),
                  let string = String(data: data, encoding: .utf8)
            else { return "[]" }
            return string
        }
    }

    /// As `StoredPath`, for every library category's path (regular and wide).
    struct StoredCategoryPaths: RawRepresentable, Equatable {
        var paths: [LibraryCategory: [Route]]

        init(_ paths: [LibraryCategory: [Route]] = [:]) {
            self.paths = paths
        }

        /// As `StoredPath.init?(rawValue:)`: malformed JSON or an unrecognised route decodes to empty.
        init?(rawValue: String) {
            guard let data = rawValue.data(using: .utf8) else { return nil }
            self.paths = (try? JSONDecoder().decode([LibraryCategory: [Route]].self, from: data)) ?? [:]
        }

        var rawValue: String {
            guard let data = try? JSONEncoder().encode(paths),
                  let string = String(data: data, encoding: .utf8)
            else { return "{}" }
            return string
        }
    }

    /// A snapshot of every library category's path, for `@SceneStorage` to observe with `onChange`.
    var categoryPathsSnapshot: StoredCategoryPaths {
        StoredCategoryPaths(libraryCategoryPaths)
    }

    /// Restores every path from `@SceneStorage` once, at launch.
    func restore(home: StoredPath, library: StoredPath, search: StoredPath, categories: StoredCategoryPaths) {
        homePath = home.routes
        libraryPath = library.routes
        searchPath = search.routes
        libraryCategoryPaths = categories.paths
    }
}
