import SwiftUI

/// The screen for every `Route`, keyed by case. `navigationDestination(for: Route.self)` and each library
/// category's own root (regular/wide) resolve to this (`docs/architecture/ios-port/phase-5-ios-app.md` section 3).
struct RouteDestinationView: View {
    let route: Route

    var body: some View {
        switch route {
        case .libraryCategory(.songs):
            SongListView()
        case .libraryCategory(.albums):
            AlbumListView()
        case .sources:
            SourcesView()
        case .serverSignIn(let name):
            if let type = Route.serverType(named: name) {
                ServerSignInView(type: type)
            } else {
                Text("Unknown server type")
            }
        case .libraryCategory(.albumArtists):
            AlbumArtistListView()
        case .libraryCategory(.genres):
            GenreListView()
        case .libraryCategory(.playlists):
            PlaylistListView()
        case .album(let albumKey, let albumArtistKey):
            AlbumDetailView(albumKey: albumKey, albumArtistKey: albumArtistKey)
        case .albumArtist(let albumArtistKey):
            AlbumArtistDetailView(albumArtistKey: albumArtistKey)
        case .genre(let name):
            GenreDetailView(name: name)
        case .playlist(let id):
            PlaylistDetailView(id: id)
        case .smartPlaylist(let id):
            SmartPlaylistDetailView(id: id)
        }
    }
}

extension View {
    /// One `navigationDestination` for every stack, mapping each pushed `Route` to its screen, with the mini player
    /// inset as on the root. Apply it to the stack's root screen, inside the `NavigationStack`: on the stack itself
    /// SwiftUI ignores it and no `NavigationLink` pushes.
    func routeDestinations(showNowPlaying: Binding<Bool>) -> some View {
        navigationDestination(for: Route.self) { route in
            RouteDestinationView(route: route)
                .miniPlayerInset(showNowPlaying: showNowPlaying)
        }
    }
}
