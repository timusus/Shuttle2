import SwiftUI

/// Placeholder content for every `Route`, keyed by case. `navigationDestination(for: Route.self)` and each
/// library category's own root (regular/wide) resolve to this until P5-5/6 replace it screen by screen
/// (`docs/architecture/ios-port/phase-5-ios-app.md` section 3). Never references Kotlin: P5-3 only wires
/// navigation, not data.
struct RouteDestinationView: View {
    let route: Route

    var body: some View {
        Text(title)
            .navigationTitle(title)
    }

    private var title: String {
        switch route {
        case .libraryCategory(let category): category.title
        case .album(let albumKey, let albumArtistKey): "Album \(albumKey ?? "?") / \(albumArtistKey ?? "?")"
        case .albumArtist(let albumArtistKey): "Album artist \(albumArtistKey ?? "?")"
        case .genre(let name): "Genre \(name)"
        case .playlist(let id): "Playlist \(id)"
        case .smartPlaylist(let id): "Smart playlist \(id)"
        }
    }
}

extension View {
    /// One `navigationDestination` for every stack, mapping each pushed `Route` to its placeholder.
    func routeDestinations() -> some View {
        navigationDestination(for: Route.self) { route in
            RouteDestinationView(route: route)
        }
    }
}
