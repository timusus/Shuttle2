import SwiftUI

/// The screen for every `Route`, keyed by case. `navigationDestination(for: Route.self)` and each library
/// category's own root (regular/wide) resolve to this. Songs and Albums are real (P5-6a); the rest are placeholders
/// until P5-6/7 replace them screen by screen (`docs/architecture/ios-port/phase-5-ios-app.md` section 3).
struct RouteDestinationView: View {
    let route: Route

    var body: some View {
        switch route {
        case .libraryCategory(.songs):
            SongListView()
        case .libraryCategory(.albums):
            AlbumListView()
        default:
            Text(title)
                .navigationTitle(title)
        }
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
    /// One `navigationDestination` for every stack, mapping each pushed `Route` to its screen. Apply it to the stack's
    /// root screen, inside the `NavigationStack`: on the stack itself SwiftUI ignores it and no `NavigationLink` pushes.
    func routeDestinations() -> some View {
        navigationDestination(for: Route.self) { route in
            RouteDestinationView(route: route)
        }
    }
}
