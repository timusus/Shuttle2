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
        case .server(let type):
            ServerDetailView(typeName: type)
        case .libraryCategory(.albumArtists):
            AlbumArtistListView()
        case .libraryCategory(.genres):
            GenreListView()
        case .libraryCategory(.playlists):
            PlaylistListView()
        case .album(let albumKey, let albumArtistKey, let albumIdentity):
            // Album and artist tiles (Home's shelves, an artist's albums) zoom into their screen on iOS 18+; the
            // source's id is the same `cacheKey`.
            AlbumDetailView(albumKey: albumKey, albumArtistKey: albumArtistKey, albumIdentity: albumIdentity)
                .zoomDestination(id: route.cacheKey)
        case .albumArtist(let albumArtistKey):
            AlbumArtistDetailView(albumArtistKey: albumArtistKey)
                .zoomDestination(id: route.cacheKey)
        case .genre(let name):
            GenreDetailView(name: name)
        case .playlist(let id):
            PlaylistDetailView(id: id)
        case .smartPlaylist(let id):
            SmartPlaylistDetailView(id: id)
        case .equalizer:
            EqualizerView()
        }
    }
}

extension View {
    /// One `navigationDestination` for every stack, mapping each pushed `Route` to its screen, with the mini player
    /// inset as on the root. Apply it to the stack's root screen, inside the `NavigationStack`: on the stack itself
    /// SwiftUI ignores it and no `NavigationLink` pushes.
    ///
    /// It also gives the stack its zoom namespace (`\.zoomNamespace`), shared by the root and every pushed screen so
    /// a tile on either zooms into the screen it opens; one set higher up (`ContentView`) wins.
    func routeDestinations(showNowPlaying: Binding<Bool>) -> some View {
        modifier(RouteDestinationsModifier(showNowPlaying: showNowPlaying))
    }
}

private struct RouteDestinationsModifier: ViewModifier {
    let showNowPlaying: Binding<Bool>

    @Environment(\.zoomNamespace) private var inheritedNamespace
    @Namespace private var stackNamespace

    func body(content: Content) -> some View {
        let namespace = inheritedNamespace ?? stackNamespace
        content
            .environment(\.zoomNamespace, namespace)
            .navigationDestination(for: Route.self) { route in
                RouteDestinationView(route: route)
                    .miniPlayerInset(showNowPlaying: showNowPlaying)
                    .environment(\.zoomNamespace, namespace)
            }
    }
}
