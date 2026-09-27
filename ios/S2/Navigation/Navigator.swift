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
}

/// The shell's navigation state: the selected tab and one path per tab's `NavigationStack`.
///
/// Owned above the layout, so a tier change (rotation, Split View drag), which swaps the tab bar for the
/// sidebar, keeps the selected tab and every pushed screen.
@Observable
final class Navigator {
    var selectedTab: AppTab = .library
    var homePath = NavigationPath()
    var libraryPath = NavigationPath()
    var searchPath = NavigationPath()
}
